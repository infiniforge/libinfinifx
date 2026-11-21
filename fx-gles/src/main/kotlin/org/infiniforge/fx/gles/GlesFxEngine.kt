package org.infiniforge.fx.gles

import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES30
import org.infiniforge.fx.api.*

data class GlesFxDiagnostics(
    val allocatedTextureBytes: Long,
    val compiledProgramCount: Int,
    val glesVersion: String,
    val glslVersion: String,
    val maxTextureSize: Int,
)

/**
 * GLES 3.0 graph executor. Construct, render, resize, reset and close on the same thread
 * with this engine's EGL context current.
 */
class GlesFxEngine(
    private val renderTargetBudgetBytes: Long = 128L * 1024L * 1024L,
) : FxEngine {
    private val ownerThread = Thread.currentThread()
    private val context: EGLContext
    val capabilities: GlesCapabilities
    private val pool: SurfacePool
    private val passes = GlesPassRenderer()
    private val runtimes = mutableMapOf<String, Pair<String, FxEffectRuntime>>()
    private val parameterOverrides = mutableMapOf<String, Map<String, FxParameterValue>>()
    private var descriptorsByNode = emptyMap<String, FxEffectDescriptor>()
    private var closed = false
    private var contextLost = false
    private var lastSize: Pair<Int, Int>? = null

    init {
        require(renderTargetBudgetBytes >= 4L * 1024L * 1024L)
        context = EGL14.eglGetCurrentContext()
        require(context != EGL14.EGL_NO_CONTEXT) {
            "Create GlesFxEngine on the render thread after an EGL/GLES context is current"
        }
        capabilities = GlesCapabilities.query()
        pool = SurfacePool(capabilities.maxTextureSize, renderTargetBudgetBytes)
    }

    override fun render(graph: FxGraph, frame: FxFrameInput, target: FxRenderTarget): FxRenderResult {
        checkThread()
        if (contextLost) return FxRenderResult.Failure("context-lost", "This engine belonged to a lost GL context; create a new engine.")
        if (closed) return FxRenderResult.Failure("closed", "FX engine is closed.")
        if (target.width != frame.geometry.destinationWidth || target.height != frame.geometry.destinationHeight) {
            return FxRenderResult.Failure("invalid-target", "Output target size must match destination geometry.")
        }
        if (target.width > capabilities.maxTextureSize || target.height > capabilities.maxTextureSize) {
            return FxRenderResult.Failure("texture-limit", "Output exceeds GLES maximum texture size ${capabilities.maxTextureSize}.")
        }
        val inputTexture = frame.sourceTexture
        if (inputTexture?.target == FxTextureTarget.EXTERNAL_OES) {
            return FxRenderResult.Failure("unsupported-texture-target", "External OES input requires a host copy adapter to GL_TEXTURE_2D.")
        }
        if ((graph.outputNodeId == FxGraph.SOURCE_NODE_ID || graph.connections.any { it.fromNodeId == FxGraph.SOURCE_NODE_ID }) &&
            inputTexture == null) {
            return FxRenderResult.Failure("missing-source", "This graph reads the source image, but the frame has no source texture.")
        }
        if (inputTexture?.name == 0) {
            return FxRenderResult.Failure("invalid-source", "Texture name 0 is not a valid source texture.")
        }
        if (inputTexture != null && target.attachedTextureName == inputTexture.name) {
            return FxRenderResult.Failure("feedback-loop", "Source and output refer to the same texture; render through a separate target.")
        }
        val sourceTexture = inputTexture?.copy(
            uvRect = frame.geometry.contentRect,
            orientationDegrees = frame.geometry.displayOrientationDegrees,
        )
        val bypassTarget = target.copy(viewport = frame.geometry.displayRect.within(target.viewport))
        val outputTarget = target.copy(viewport = target.viewport)

        val plan = try {
            FxGraphValidator.validate(graph, BuiltinFx.byId)
        } catch (e: FxGraphValidationException) {
            return FxRenderResult.Failure("invalid-graph", e.message ?: "Graph validation failed.")
        }
        plan.orderedNodes.forEach { node ->
            val descriptor = BuiltinFx.byId[node.effectId] ?: return FxRenderResult.Failure(
                "unsupported-effect", "No runtime is registered for '${node.effectId}'.", node.id,
            )
            if (!capabilities.supports(descriptor.requiredCapabilities)) {
                return FxRenderResult.Failure("unsupported-capability", "Effect '${descriptor.id}' needs capabilities not present on this device.", node.id)
            }
        }
        val incoming = graph.connections.groupBy { it.toNodeId }
        val activeNodes = plan.orderedNodes.associateBy { it.id }
        pruneRuntimes(activeNodes)
        descriptorsByNode = activeNodes.mapValues { (_, node) -> BuiltinFx.byId.getValue(node.effectId) }
        val liveCounts = plan.consumers.toMutableMap()
        val outputSurfaces = mutableMapOf<String, FxSurface>()
        val acquiredThisFrame = mutableSetOf<FxSurface>()
        val state = GlesStateSnapshot()
        try {
            val size = target.width to target.height
            if (lastSize != size) {
                lastSize = size
                runtimes.values.forEach { it.second.reset(FxDiscontinuity.SOURCE_RESIZED) }
                pool.trimFree()
            }

            if (graph.outputNodeId == FxGraph.SOURCE_NODE_ID) {
                val source = sourceTexture ?: return FxRenderResult.Failure("missing-source", "Pass-through needs a source texture.")
                passes.draw(FxShaders.COPY, bypassTarget, listOf(source), mapOf("uInput" to 0))
                return FxRenderResult.Success()
            }

            // Normalize input to destination geometry once so graph branches and sequential
            // passes preserve source aspect instead of stretching intermediate textures.
            var graphSourceTexture = sourceTexture
            if (sourceTexture != null && graph.connections.any { it.fromNodeId == FxGraph.SOURCE_NODE_ID }) {
                val normalized = pool.acquire(target.width, target.height).also(acquiredThisFrame::add)
                passes.clear(normalized.target())
                passes.draw(
                    FxShaders.COPY,
                    normalized.target().copy(viewport = frame.geometry.displayRect.within(target.viewport)),
                    listOf(sourceTexture),
                    mapOf("uInput" to 0),
                )
                graphSourceTexture = normalized.input()
            }

            for (node in plan.orderedNodes) {
                val descriptor = BuiltinFx.byId.getValue(node.effectId)
                val parameters = FxGraphValidator.validateParameters(
                    descriptor,
                    node.parameters + parameterOverrides[node.id].orEmpty(),
                )
                val edges = incoming[node.id].orEmpty()
                val inputs = mutableMapOf<String, FxTexture>()
                for (edge in edges) {
                    val texture = if (edge.fromNodeId == FxGraph.SOURCE_NODE_ID) {
                        graphSourceTexture
                    } else {
                        outputSurfaces[edge.fromNodeId]?.input()
                    }
                    if (texture == null) {
                        return FxRenderResult.Failure("missing-input", "Input '${edge.inputPort}' is not available.", node.id)
                    }
                    inputs[edge.inputPort] = texture
                }

                val isOutput = node.id == graph.outputNodeId
                val outputSurface = if (isOutput) null else pool.acquire(target.width, target.height).also(acquiredThisFrame::add)
                val nodeTarget = outputSurface?.target() ?: outputTarget
                if (!node.enabled) {
                    when {
                        "base" in inputs -> passes.draw(FxShaders.COPY, nodeTarget, listOf(inputs.getValue("base")), mapOf("uInput" to 0))
                        "image" in inputs -> passes.draw(FxShaders.COPY, nodeTarget, listOf(inputs.getValue("image")), mapOf("uInput" to 0))
                        else -> passes.clear(nodeTarget)
                    }
                } else {
                    val runtime = runtimeFor(node.id, descriptor)
                    runtime.render(frame, inputs, nodeTarget, parameters)
                }

                if (outputSurface != null) outputSurfaces[node.id] = outputSurface
                edges.forEach { edge ->
                    val left = (liveCounts[edge.fromNodeId] ?: 0) - 1
                    liveCounts[edge.fromNodeId] = left
                    if (left <= 0) outputSurfaces.remove(edge.fromNodeId)?.let { surface ->
                        pool.release(surface)
                        acquiredThisFrame.remove(surface)
                    }
                }
            }
            return FxRenderResult.Success()
        } catch (e: IllegalStateException) {
            val text = e.message ?: "GLES render failed."
            val code = when {
                text.contains("shader compile", ignoreCase = true) || text.contains("program link", ignoreCase = true) -> "shader-error"
                text.contains("budget", ignoreCase = true) || text.contains("texture size", ignoreCase = true) -> "resource-limit"
                text.contains("feedback", ignoreCase = true) -> "feedback-loop"
                else -> "gles-error"
            }
            return FxRenderResult.Failure(code, text)
        } catch (e: IllegalArgumentException) {
            return FxRenderResult.Failure("invalid-parameter", e.message ?: "Invalid graph parameter.")
        } finally {
            acquiredThisFrame.forEach(pool::release)
            state.restore()
        }
    }

    override fun resize(width: Int, height: Int) {
        checkThread()
        require(width > 0 && height > 0 && width <= capabilities.maxTextureSize && height <= capabilities.maxTextureSize)
        val state = GlesStateSnapshot()
        try {
            lastSize = width to height
            runtimes.values.forEach { it.second.reset(FxDiscontinuity.SOURCE_RESIZED) }
            pool.trimFree()
        } finally {
            state.restore()
        }
    }

    override fun resetHistory(reason: FxDiscontinuity) {
        checkThread()
        if (reason == FxDiscontinuity.CONTEXT_LOST) {
            contextLost = true
            runtimes.clear()
            parameterOverrides.clear()
            pool.abandonAfterContextLoss()
            passes.abandonAfterContextLoss()
            return
        }
        val state = GlesStateSnapshot()
        try {
            runtimes.values.forEach { it.second.reset(reason) }
        } finally {
            state.restore()
        }
    }

    override fun updateParameters(nodeId: String, parameters: Map<String, FxParameterValue>) {
        checkThread()
        check(!closed && !contextLost) { "FX engine is unavailable" }
        val descriptor = descriptorsByNode[nodeId] ?: error("Render the graph before updating parameters for node '$nodeId'.")
        val merged = parameterOverrides[nodeId].orEmpty() + parameters
        FxGraphValidator.validateParameters(descriptor, merged)
        parameterOverrides[nodeId] = merged.toMap()
    }

    fun diagnostics() = GlesFxDiagnostics(
        allocatedTextureBytes = pool.allocatedBytes,
        compiledProgramCount = passes.programCount(),
        glesVersion = capabilities.version,
        glslVersion = capabilities.shadingLanguage,
        maxTextureSize = capabilities.maxTextureSize,
    )

    override fun close() {
        checkThread()
        if (closed) return
        if (!contextLost) {
            runtimes.values.forEach { it.second.close() }
            runtimes.clear()
            pool.releaseAll()
            passes.close()
        }
        closed = true
    }

    private fun runtimeFor(nodeId: String, descriptor: FxEffectDescriptor): FxEffectRuntime {
        val current = runtimes[nodeId]
        if (current != null && current.first == descriptor.id) return current.second
        current?.second?.close()
        return BuiltinRuntime(descriptor.id, passes, pool).also { runtimes[nodeId] = descriptor.id to it }
    }

    private fun pruneRuntimes(active: Map<String, FxNode>) {
        val iterator = runtimes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val expected = active[entry.key]?.effectId
            if (expected != entry.value.first) {
                entry.value.second.close()
                iterator.remove()
                parameterOverrides.remove(entry.key)
            }
        }
        parameterOverrides.keys.retainAll(active.keys)
    }

    private fun checkThread() {
        check(Thread.currentThread() === ownerThread) { "GLES FX methods must run on the engine's creation thread." }
        check(EGL14.eglGetCurrentContext() == context || contextLost) { "FX engine's EGL context is not current." }
    }
}


private fun FxRect.within(parent: FxRect): FxRect {
    val width = parent.right - parent.left
    val height = parent.top - parent.bottom
    return FxRect(
        parent.left + left * width,
        parent.bottom + bottom * height,
        parent.left + right * width,
        parent.bottom + top * height,
    )
}
