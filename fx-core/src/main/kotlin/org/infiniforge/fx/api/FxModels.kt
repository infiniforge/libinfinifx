package org.infiniforge.fx.api

import java.util.Collections

/** Opaque texture name. The caller owns input textures. */
data class FxTexture(
    val name: Int, val width: Int, val height: Int,
    val target: FxTextureTarget = FxTextureTarget.TEXTURE_2D,
    val alphaMode: FxAlphaMode = FxAlphaMode.PREMULTIPLIED,
    val colorEncoding: FxColorEncoding = FxColorEncoding.SRGB,
    val sampling: FxSampling = FxSampling.LINEAR,
    val uvRect: FxRect = FxRect(0f, 0f, 1f, 1f),
    val orientationDegrees: Int = 0,
) {
    init {
        require(name > 0) { "Texture name must be positive" }
        require(width > 0 && height > 0) { "Texture dimensions must be positive" }
        require(orientationDegrees in setOf(0, 90, 180, 270))
    }
}
enum class FxTextureTarget { TEXTURE_2D, EXTERNAL_OES }
enum class FxAlphaMode { OPAQUE, STRAIGHT, PREMULTIPLIED }
enum class FxColorEncoding { LINEAR, SRGB, UNKNOWN }
enum class FxSampling { NEAREST, LINEAR }
enum class FxWorkingColorSpace { LINEAR_SRGB }
enum class FxDiscontinuity { SEEK, TRACK_CHANGED, PAUSED, RESUMED, SOURCE_RESIZED, CONTEXT_LOST, RESET }

data class FxRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    init {
        require(listOf(left, bottom, right, top).all(Float::isFinite))
        require(left in 0f..1f && bottom in 0f..1f && right in 0f..1f && top in 0f..1f)
        require(left < right && bottom < top) { "Rectangle must have positive area inside normalized GL coordinates" }
    }
}
data class FxGeometry(
    val sourceWidth: Int, val sourceHeight: Int,
    val destinationWidth: Int, val destinationHeight: Int,
    val pixelAspectRatio: Float = 1f,
    val displayOrientationDegrees: Int = 0,
    val contentRect: FxRect = FxRect(0f, 0f, 1f, 1f),
    val displayRect: FxRect = FxRect(0f, 0f, 1f, 1f),
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0 && destinationWidth > 0 && destinationHeight > 0)
        require(pixelAspectRatio.isFinite() && pixelAspectRatio > 0f)
        require(displayOrientationDegrees in setOf(0, 90, 180, 270)) { "Orientation must be 0, 90, 180, or 270 degrees" }
    }
    val orientedSourceWidth: Int get() = if (displayOrientationDegrees == 90 || displayOrientationDegrees == 270) sourceHeight else sourceWidth
    val orientedSourceHeight: Int get() = if (displayOrientationDegrees == 90 || displayOrientationDegrees == 270) sourceWidth else sourceHeight
}

/** Stable timestamped event; reusing a frame snapshot must not replay it. */
data class FxAudioEvent(val id: Long, val timestampNanos: Long, val strength: Float) {
    init { require(id >= 0 && timestampNanos >= 0 && strength.isFinite() && strength in 0f..1f) }
}

/** Per-effect cursor for host-assigned, monotonically increasing audio event IDs. */
class FxEventCursor {
    private var lastId = -1L

    /** Returns true once for each increasing ID. Reused frame snapshots return false. */
    fun accept(eventId: Long): Boolean {
        require(eventId >= 0) { "Event ID must be nonnegative" }
        if (eventId <= lastId) return false
        lastId = eventId
        return true
    }

    fun reset() { lastId = -1L }
}

/** Audio values are normalized to [0,1]. Invalid floats become zero; missing bands stay absent. */
class AudioFrameMetadata(
    val timestampNanos: Long,
    rms: Float = 0f,
    peak: Float = 0f,
    bands: List<Float> = emptyList(),
    lowEnergy: Float = 0f,
    midEnergy: Float = 0f,
    highEnergy: Float = 0f,
    spectralFlux: Float = 0f,
    onsetEvents: List<FxAudioEvent> = emptyList(),
    bpm: Float? = null,
    beatPhase: Float? = null,
) {
    val rms = unit(rms)
    val peak = unit(peak)
    val bands: List<Float> = Collections.unmodifiableList(bands.take(MAX_BANDS).map(::unit))
    val lowEnergy = unit(lowEnergy)
    val midEnergy = unit(midEnergy)
    val highEnergy = unit(highEnergy)
    val spectralFlux = unit(spectralFlux)
    val onsetEvents: List<FxAudioEvent> = Collections.unmodifiableList(onsetEvents.take(MAX_EVENTS).toList())
    val bpm = bpm?.takeIf { it.isFinite() && it in 20f..400f }
    val beatPhase = beatPhase?.takeIf(Float::isFinite)?.let { ((it % 1f) + 1f) % 1f }

    init { require(timestampNanos >= 0) }
    companion object {
        const val MAX_BANDS = 64
        const val MAX_EVENTS = 32
        private fun unit(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
    }
}

/** At most 32 caller channels; keys are namespaced and values finite/clamped. */
class FxMetadataChannels(values: Map<String, Float> = emptyMap()) {
    val values: Map<String, Float> = Collections.unmodifiableMap(
        values.entries.take(MAX_CHANNELS).associate { (key, value) ->
            require(key.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}"))) { "Invalid channel key: $key" }
            key to if (value.isFinite()) value.coerceIn(-1_000f, 1_000f) else 0f
        },
    )
    companion object { const val MAX_CHANNELS = 32 }
}

/** Immutable metadata snapshot shared by every node for a render call. Times are monotonic nanoseconds. */
data class FxFrameInput(
    val presentationTimeNanos: Long,
    val deltaTimeSeconds: Float,
    val frameIndex: Long,
    val geometry: FxGeometry,
    val sourceTexture: FxTexture? = null,
    val audio: AudioFrameMetadata? = null,
    val channels: FxMetadataChannels = FxMetadataChannels(),
    val workingColorSpace: FxWorkingColorSpace = FxWorkingColorSpace.LINEAR_SRGB,
    val randomSeed: Long = 0L,
) {
    init {
        require(presentationTimeNanos >= 0 && frameIndex >= 0)
        require(deltaTimeSeconds.isFinite() && deltaTimeSeconds >= 0f)
        require(sourceTexture == null || (sourceTexture.width == geometry.sourceWidth && sourceTexture.height == geometry.sourceHeight)) {
            "Source texture dimensions must match frame geometry"
        }
    }
}

/** Caller-owned draw destination. attachedTextureName enables feedback-loop detection when known. */
data class FxRenderTarget(
    val framebufferName: Int, val width: Int, val height: Int,
    val attachedTextureName: Int? = null,
    val alphaMode: FxAlphaMode = FxAlphaMode.PREMULTIPLIED,
    val colorEncoding: FxColorEncoding = FxColorEncoding.SRGB,
    val viewport: FxRect = FxRect(0f, 0f, 1f, 1f),
) {
    init { require(framebufferName >= 0 && width > 0 && height > 0) }
}

sealed interface FxParameterValue {
    data class FloatValue(val value: Float) : FxParameterValue
    data class IntValue(val value: Int) : FxParameterValue
    data class BoolValue(val value: Boolean) : FxParameterValue
    data class EnumValue(val value: String) : FxParameterValue
    data class VectorValue(val values: List<Float>) : FxParameterValue
}
enum class FxParameterType { FLOAT, INT, BOOLEAN, ENUM, VECTOR }
enum class FxEffectCategory { GENERATOR, TRANSFORMER, COMPOSITOR, DISPLAY }
enum class FxCapability { GLES_3_0, FLOAT_RENDER_TARGET }

data class FxParameterDescriptor(
    val name: String, val type: FxParameterType, val defaultValue: FxParameterValue,
    val minimum: Float? = null, val maximum: Float? = null, val units: String? = null,
    val description: String, val enumValues: List<String> = emptyList(),
)
data class FxInputPort(val name: String, val required: Boolean = true)
data class FxEffectDescriptor(
    val id: String, val version: Int, val title: String, val description: String,
    val category: FxEffectCategory, val inputPorts: List<FxInputPort>,
    val metadataInterests: Set<String> = emptySet(), val needsHistory: Boolean = false,
    val requiredCapabilities: Set<FxCapability> = setOf(FxCapability.GLES_3_0),
    val predictablePassCount: Int = 1, val deterministicWithFixedInputs: Boolean = true,
    val parameters: List<FxParameterDescriptor> = emptyList(),
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*")))
        require(version > 0 && predictablePassCount > 0)
        require(parameters.map { it.name }.distinct().size == parameters.size)
        require(inputPorts.map { it.name }.distinct().size == inputPorts.size)
    }
}

data class FxNode(
    val id: String, val effectId: String,
    val parameters: Map<String, FxParameterValue> = emptyMap(),
    val enabled: Boolean = true,
)
data class FxConnection(val fromNodeId: String, val toNodeId: String, val inputPort: String)
data class FxGraph(
    val name: String, val nodes: List<FxNode>, val connections: List<FxConnection>,
    val outputNodeId: String = SOURCE_NODE_ID,
) {
    companion object { const val SOURCE_NODE_ID = "source"; const val MAX_NODES = 64; const val MAX_CONNECTIONS = 128 }
}
data class FxPreset(val name: String, val description: String, val graph: FxGraph, val schemaVersion: Int = 1) {
    init { require(schemaVersion > 0) }
}
data class FxDiagnostic(val code: String, val message: String, val nodeId: String? = null)
sealed interface FxRenderResult {
    data class Success(val diagnostics: List<FxDiagnostic> = emptyList()) : FxRenderResult
    data class Failure(val code: String, val message: String, val nodeId: String? = null) : FxRenderResult
}
enum class FxErrorCode {
    INVALID_GRAPH, UNSUPPORTED_EFFECT, UNSUPPORTED_CAPABILITY, UNSUPPORTED_TEXTURE_TARGET,
    MISSING_SOURCE, WRONG_THREAD, INVALID_TARGET, RESOURCE_LIMIT, SHADER_COMPILE, GL_FAILURE,
}
class FxGraphValidationException(val diagnostics: List<FxDiagnostic>) :
    IllegalArgumentException(diagnostics.joinToString("; ") { it.message })
data class FxExecutionPlan(val orderedNodes: List<FxNode>, val consumers: Map<String, Int>)

/** GPU implementations require the current GLES context and owning GL thread for all methods. */
interface FxEngine : AutoCloseable {
    fun render(graph: FxGraph, frame: FxFrameInput, target: FxRenderTarget): FxRenderResult
    fun resize(width: Int, height: Int)
    fun resetHistory(reason: FxDiscontinuity)
    fun updateParameters(nodeId: String, parameters: Map<String, FxParameterValue>)
    override fun close()
}
