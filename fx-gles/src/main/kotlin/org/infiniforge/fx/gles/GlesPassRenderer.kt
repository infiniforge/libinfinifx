package org.infiniforge.fx.gles

import android.opengl.GLES30
import org.infiniforge.fx.api.FxRenderTarget
import org.infiniforge.fx.api.FxTexture
import org.infiniforge.fx.api.FxTextureTarget
import kotlin.math.roundToInt

internal data class FxSurface(val texture: Int, val framebuffer: Int, val width: Int, val height: Int) {
    fun target() = FxRenderTarget(framebuffer, width, height, texture)
    fun input() = FxTexture(texture, width, height)
}

internal class SurfacePool(
    private val maxTextureSize: Int,
    private val maxBytes: Long,
) {
    private val all = linkedSetOf<FxSurface>()
    private val free = mutableListOf<FxSurface>()
    private val leased = mutableSetOf<FxSurface>()
    var allocatedBytes: Long = 0
        private set

    fun acquire(width: Int, height: Int): FxSurface {
        require(width in 1..maxTextureSize && height in 1..maxTextureSize) {
            "Render target ${width}x${height} exceeds GLES max texture size ${maxTextureSize}"
        }
        val index = free.indexOfFirst { it.width == width && it.height == height }
        val surface = if (index >= 0) free.removeAt(index) else allocate(width, height)
        check(leased.add(surface)) { "Internal render target lease is already active" }
        return surface
    }

    fun release(surface: FxSurface) {
        if (leased.remove(surface)) free += surface
    }

    /** Include cached non-FBO textures, such as generated glyph strips, in the shared GPU budget. */
    fun reserveExternalTexture(width: Int, height: Int): Long {
        require(width in 1..maxTextureSize && height in 1..maxTextureSize) {
            "Generated texture ${width}x${height} exceeds GLES max texture size ${maxTextureSize}"
        }
        val bytes = width.toLong() * height.toLong() * 4L
        while (allocatedBytes + bytes > maxBytes && free.isNotEmpty()) delete(free.removeAt(0))
        check(allocatedBytes + bytes <= maxBytes) {
            "FX render-target budget exceeded: generated texture needs $bytes bytes with $allocatedBytes/$maxBytes already allocated"
        }
        allocatedBytes += bytes
        return bytes
    }

    fun releaseExternalTexture(bytes: Long) {
        require(bytes >= 0 && bytes <= allocatedBytes) { "Invalid external texture budget release" }
        allocatedBytes -= bytes
    }

    fun trimFree() {
        val discarded = free.toList()
        free.clear()
        discarded.forEach(::delete)
    }

    fun releaseAll() {
        all.toList().forEach(::delete)
        all.clear()
        free.clear()
        leased.clear()
        allocatedBytes = 0
    }

    fun abandonAfterContextLoss() {
        all.clear()
        free.clear()
        leased.clear()
        allocatedBytes = 0
    }

    private fun allocate(width: Int, height: Int): FxSurface {
        val bytes = width.toLong() * height.toLong() * 4L
        while (allocatedBytes + bytes > maxBytes && free.isNotEmpty()) delete(free.removeAt(0))
        if (allocatedBytes + bytes > maxBytes) {
            throw IllegalStateException("FX render-target budget exceeded: requested $bytes bytes with $allocatedBytes/$maxBytes already allocated")
        }
        val textureNames = IntArray(1)
        val framebufferNames = IntArray(1)
        GLES30.glGenTextures(1, textureNames, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureNames[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glGenFramebuffers(1, framebufferNames, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferNames[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textureNames[0], 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, framebufferNames, 0)
            GLES30.glDeleteTextures(1, textureNames, 0)
            throw IllegalStateException("RGBA8 render target is incomplete (0x${status.toString(16)})")
        }
        return FxSurface(textureNames[0], framebufferNames[0], width, height).also {
            all += it
            allocatedBytes += bytes
        }
    }

    private fun delete(surface: FxSurface) {
        GLES30.glDeleteFramebuffers(1, intArrayOf(surface.framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(surface.texture), 0)
        all.remove(surface)
        leased.remove(surface)
        allocatedBytes -= surface.width.toLong() * surface.height.toLong() * 4L
    }
}

internal class GlesPassRenderer {
    private val programs = mutableMapOf<String, Int>()

    fun draw(
        fragmentSource: String,
        target: FxRenderTarget,
        textures: List<FxTexture>,
        uniforms: Map<String, Any> = emptyMap(),
    ) {
        require(textures.size <= 2) { "Built-in passes accept at most two texture inputs" }
        if (target.attachedTextureName != null && textures.any { it.name == target.attachedTextureName }) {
            error("FX pass would sample from the texture attached to its output framebuffer")
        }
        val program = program(fragmentSource)
        val oldFilters = linkedMapOf<Int, Pair<Int, Int>>()
        try {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebufferName)
            if (target.framebufferName != 0) {
                val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Output framebuffer is incomplete (0x${status.toString(16)})" }
            }
            val viewport = target.viewport
            val vx = (viewport.left * target.width).roundToInt().coerceIn(0, target.width - 1)
            val vy = (viewport.bottom * target.height).roundToInt().coerceIn(0, target.height - 1)
            val vw = ((viewport.right - viewport.left) * target.width).roundToInt().coerceAtLeast(1).coerceAtMost(target.width - vx)
            val vh = ((viewport.top - viewport.bottom) * target.height).roundToInt().coerceAtLeast(1).coerceAtMost(target.height - vy)
            GLES30.glViewport(vx, vy, vw, vh)
            GLES30.glUseProgram(program)
            textures.forEachIndexed { index, texture ->
                check(texture.target == FxTextureTarget.TEXTURE_2D) { "External OES textures need an explicit copy adapter" }
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + index)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.name)
                if (texture.name !in oldFilters) {
                    val min = IntArray(1)
                    val mag = IntArray(1)
                    GLES30.glGetTexParameteriv(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, min, 0)
                    GLES30.glGetTexParameteriv(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, mag, 0)
                    oldFilters[texture.name] = min[0] to mag[0]
                }
                val filter = if (texture.sampling == org.infiniforge.fx.api.FxSampling.NEAREST) GLES30.GL_NEAREST else GLES30.GL_LINEAR
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
            }
            textures.forEachIndexed { index, texture ->
                val alphaMode = when (texture.alphaMode) {
                    org.infiniforge.fx.api.FxAlphaMode.OPAQUE -> 0
                    org.infiniforge.fx.api.FxAlphaMode.STRAIGHT -> 1
                    org.infiniforge.fx.api.FxAlphaMode.PREMULTIPLIED -> 2
                }
                val colorEncoding = when (texture.colorEncoding) {
                    org.infiniforge.fx.api.FxColorEncoding.LINEAR -> 0
                    org.infiniforge.fx.api.FxColorEncoding.SRGB -> 1
                    org.infiniforge.fx.api.FxColorEncoding.UNKNOWN -> 2
                }
                setUniform(program, "uAlphaMode$index", alphaMode)
                setUniform(program, "uColorEncoding$index", colorEncoding)
                val rect = texture.uvRect
                setUniform(program, "uUvRect$index", floatArrayOf(rect.left, rect.bottom, rect.right - rect.left, rect.top - rect.bottom))
                setUniform(program, "uOrientation$index", texture.orientationDegrees)
            }
            setUniform(program, "uOutputAlphaMode", when (target.alphaMode) {
                org.infiniforge.fx.api.FxAlphaMode.OPAQUE -> 0
                org.infiniforge.fx.api.FxAlphaMode.STRAIGHT -> 1
                org.infiniforge.fx.api.FxAlphaMode.PREMULTIPLIED -> 2
            })
            setUniform(program, "uOutputColorEncoding", when (target.colorEncoding) {
                org.infiniforge.fx.api.FxColorEncoding.LINEAR -> 0
                org.infiniforge.fx.api.FxColorEncoding.SRGB -> 1
                org.infiniforge.fx.api.FxColorEncoding.UNKNOWN -> 2
            })
            uniforms.forEach { (name, value) -> setUniform(program, name, value) }
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            val error = GLES30.glGetError()
            check(error == GLES30.GL_NO_ERROR) { "GLES draw failed with GL error 0x${error.toString(16)}" }
        } finally {
            oldFilters.forEach { (name, filters) ->
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filters.first)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filters.second)
            }
        }
    }

    fun clear(target: FxRenderTarget, r: Float = 0f, g: Float = 0f, b: Float = 0f, a: Float = 0f) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebufferName)
        val viewport = target.viewport
        val x = (viewport.left * target.width).roundToInt().coerceIn(0, target.width - 1)
        val y = (viewport.bottom * target.height).roundToInt().coerceIn(0, target.height - 1)
        val width = ((viewport.right - viewport.left) * target.width).roundToInt().coerceAtLeast(1).coerceAtMost(target.width - x)
        val height = ((viewport.top - viewport.bottom) * target.height).roundToInt().coerceAtLeast(1).coerceAtMost(target.height - y)
        GLES30.glViewport(x, y, width, height)
        GLES30.glScissor(x, y, width, height)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(r, g, b, a)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
    }

    fun abandonAfterContextLoss() { programs.clear() }

    fun close() {
        programs.values.forEach { GLES30.glDeleteProgram(it) }
        programs.clear()
    }

    fun programCount() = programs.size

    private fun program(fragmentSource: String): Int {
        programs[fragmentSource]?.let { return it }
        val vertex = compile(GLES30.GL_VERTEX_SHADER, FxShaders.VERTEX)
        val preparedFragment = fragmentSource.replace("precision highp float;", "precision highp float;\n${FxShaders.COLOR_HELPERS}")
        val fragment = try { compile(GLES30.GL_FRAGMENT_SHADER, preparedFragment) } catch (t: Throwable) {
            GLES30.glDeleteShader(vertex)
            throw t
        }
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        val log = GLES30.glGetProgramInfoLog(program)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        check(status[0] == GLES30.GL_TRUE) {
            GLES30.glDeleteProgram(program)
            "GLES program link failed: $log"
        }
        programs[fragmentSource] = program
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        check(shader != 0) { "Unable to allocate GLES shader" }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        val log = GLES30.glGetShaderInfoLog(shader)
        if (status[0] != GLES30.GL_TRUE) {
            GLES30.glDeleteShader(shader)
            error("GLES shader compile failed: $log")
        }
        return shader
    }

    private fun setUniform(program: Int, name: String, value: Any) {
        val location = GLES30.glGetUniformLocation(program, name)
        if (location < 0) return
        when (value) {
            is Float -> GLES30.glUniform1f(location, value)
            is Int -> GLES30.glUniform1i(location, value)
            is Boolean -> GLES30.glUniform1i(location, if (value) 1 else 0)
            is FloatArray -> when (value.size) {
                2 -> GLES30.glUniform2fv(location, 1, value, 0)
                3 -> GLES30.glUniform3fv(location, 1, value, 0)
                4 -> GLES30.glUniform4fv(location, 1, value, 0)
                else -> error("Unsupported float uniform vector length ${value.size}")
            }
            else -> error("Unsupported uniform value for $name: ${value::class.java.name}")
        }
    }
}

/** Captures/restores the GL state the backend changes around graph execution. */
internal class GlesStateSnapshot {
    private val drawFramebuffer = getInt(GLES30.GL_DRAW_FRAMEBUFFER_BINDING)
    private val readFramebuffer = getInt(GLES30.GL_READ_FRAMEBUFFER_BINDING)
    private val viewport = IntArray(4).also { GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, it, 0) }
    private val scissorBox = IntArray(4).also { GLES30.glGetIntegerv(GLES30.GL_SCISSOR_BOX, it, 0) }
    private val program = getInt(GLES30.GL_CURRENT_PROGRAM)
    private val activeTexture = getInt(GLES30.GL_ACTIVE_TEXTURE)
    private val textureBindings = IntArray(2)
    private val blend = GLES30.glIsEnabled(GLES30.GL_BLEND)
    private val depth = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST)
    private val cull = GLES30.glIsEnabled(GLES30.GL_CULL_FACE)
    private val scissor = GLES30.glIsEnabled(GLES30.GL_SCISSOR_TEST)
    private val dither = GLES30.glIsEnabled(GLES30.GL_DITHER)
    private val colorMask = IntArray(4).also { GLES30.glGetIntegerv(GLES30.GL_COLOR_WRITEMASK, it, 0) }
    private val clearColor = FloatArray(4).also { GLES30.glGetFloatv(GLES30.GL_COLOR_CLEAR_VALUE, it, 0) }

    init {
        for (unit in 0..1) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            textureBindings[unit] = getInt(GLES30.GL_TEXTURE_BINDING_2D)
        }
        GLES30.glActiveTexture(activeTexture)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_DITHER)
        GLES30.glColorMask(true, true, true, true)
    }

    fun restore() {
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, readFramebuffer)
        GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GLES30.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
        GLES30.glUseProgram(program)
        for (unit in 0..1) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureBindings[unit])
        }
        GLES30.glActiveTexture(activeTexture)
        setEnabled(GLES30.GL_BLEND, blend)
        setEnabled(GLES30.GL_DEPTH_TEST, depth)
        setEnabled(GLES30.GL_CULL_FACE, cull)
        setEnabled(GLES30.GL_SCISSOR_TEST, scissor)
        setEnabled(GLES30.GL_DITHER, dither)
        GLES30.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0)
        GLES30.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
    }

    private fun setEnabled(capability: Int, enabled: Boolean) {
        if (enabled) GLES30.glEnable(capability) else GLES30.glDisable(capability)
    }

    private fun getInt(name: Int) = IntArray(1).also { GLES30.glGetIntegerv(name, it, 0) }[0]
}
