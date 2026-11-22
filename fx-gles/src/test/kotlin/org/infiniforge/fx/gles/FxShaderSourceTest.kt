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

    @Test fun lightningSpectrumEmitsNoPixelsForZeroHeightBands() {
        val source = FxShaderSource.normalize(LightningSpectrumShader.LIGHTNING_SPECTRUM)
        assertTrue("uAudioPresent == 1" in source)
        assertTrue("uMaxHeight * level" in source)
        assertTrue("vUv.y / height" in source)
        assertTrue("float body =" !in source)
        assertTrue("fxSrgbDecode(samplePalette(paletteIndex))" in source)
        assertTrue("slot % max(uPaletteCount, 1)" in source)
        assertTrue("uTime * clamp(uAnimationSpeed, 0.0, 40.0)" in source)
        assertTrue("int branchCount = clamp(uBranchCount, 1, 8)" in source)
        assertTrue("for (int branch = 0; branch < 8; branch++)" in source)
        assertTrue("float bandPixelAa = max(fwidth(contentX) * bandsAcross, 0.00001)" in source)
        assertTrue("float spineAa = clamp(fwidth(spineDistance), bandPixelAa, bandPixelAa * 2.0)" in source)
        assertTrue("float branchAa = clamp(fwidth(branchDistance), bandPixelAa, bandPixelAa * 2.0)" in source)
        assertTrue("float twigAa = clamp(fwidth(twigDistance), bandPixelAa, bandPixelAa * 2.0)" in source)
        assertTrue("smoothstep(spineAa * 0.08, spineAa * 0.72, spineDistance)" in source)
        assertTrue("return 0.5 + broadJitter + fineJitter;" in source)
        assertTrue("return clamp(0.5 + broadJitter + fineJitter" !in source)
        assertTrue("float twigLine =" in source)
        assertTrue("float randomSignal(float coordinate, float key)" in source)
        assertTrue("sin(" !in source)
        assertTrue("boltWidth" !in source)
        assertTrue("uBarGap" !in source)
        assertTrue("uMaxHeight * level * 0.92" !in source)
        assertTrue("if (height <= 0.000001)" in source)
        assertTrue("fragColor = vec4(0.0);" in source)
    }

    @Test fun normalizesSharedHelpersBeforeInjection() {
        val helpers = FxShaderSource.normalize(FxShaders.COLOR_HELPERS)
        assertTrue(helpers.startsWith("vec3 fxSrgbDecode"))
        assertTrue(helpers.none { it == '\r' })
    }
}
