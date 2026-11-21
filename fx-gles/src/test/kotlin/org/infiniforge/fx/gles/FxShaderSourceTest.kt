package org.infiniforge.fx.gles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FxShaderSourceTest {
    @Test fun normalizesAllBuiltInShaderDirectivesToTheFirstLine() {
        val shaders = listOf(
            FxShaders.VERTEX,
            FxShaders.COPY,
            FxShaders.CRT,
            FxShaders.LCD,
            FxShaders.ROTOZOOM,
            FxShaders.KALEIDOSCOPE,
            FxShaders.WATER,
            FxShaders.HEAT_STEP,
            FxShaders.HEAT_RIPPLE,
            FxShaders.FIRE,
            FxShaders.BLEND,
            LightningSpectrumShader.LIGHTNING_SPECTRUM,
        )
        shaders.forEach { source ->
            val normalized = FxShaderSource.normalize(source)
            assertEquals("#version 300 es", normalized.lineSequence().first())
            assertTrue(normalized.startsWith("#version"))
        }
    }

    @Test fun normalizesSharedHelpersBeforeInjection() {
        val helpers = FxShaderSource.normalize(FxShaders.COLOR_HELPERS)
        assertTrue(helpers.startsWith("vec3 fxSrgbDecode"))
        assertTrue(helpers.none { it == '\r' })
    }
}
