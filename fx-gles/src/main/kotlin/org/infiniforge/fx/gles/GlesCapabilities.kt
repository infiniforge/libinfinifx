package org.infiniforge.fx.gles

import android.opengl.GLES30
import org.infiniforge.fx.api.FxCapability

data class GlesCapabilities(
    val version: String,
    val shadingLanguage: String,
    val maxTextureSize: Int,
    val extensions: Set<String>,
) {
    val supportsExternalTextureEs3: Boolean
        get() = "GL_OES_EGL_image_external_essl3" in extensions

    fun supports(required: Set<FxCapability>): Boolean = required.all {
        when (it) {
            FxCapability.GLES_3_0 -> version.contains("OpenGL ES 3")
            FxCapability.FLOAT_RENDER_TARGET -> false // RGBA8 is the portable built-in path.
        }
    }

    companion object {
        fun query(): GlesCapabilities {
            val version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty()
            require(version.contains("OpenGL ES 3")) { "libinfinifx requires an active OpenGL ES 3.0 context; found '$version'" }
            val shading = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION).orEmpty()
            val max = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
            val extensionCount = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_NUM_EXTENSIONS, extensionCount, 0)
            val extensions = buildSet {
                for (index in 0 until extensionCount[0]) GLES30.glGetStringi(GLES30.GL_EXTENSIONS, index)?.let(::add)
            }
            return GlesCapabilities(version, shading, max[0], extensions)
        }
    }
}
