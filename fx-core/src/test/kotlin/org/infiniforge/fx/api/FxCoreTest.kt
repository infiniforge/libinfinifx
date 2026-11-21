package org.infiniforge.fx.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FxCoreTest {
    @Test fun defaultsAndRangesAreChecked() {
        val crt = BuiltinFx.byId.getValue("display:crt")
        assertEquals(.28f, (crt.parameters.first().defaultValue as FxParameterValue.FloatValue).value)
        assertFailsWith<FxGraphValidationException> {
            FxGraphValidator.validate(
                FxGraph("bad", listOf(FxNode("crt", crt.id, mapOf("scanlineStrength" to FxParameterValue.FloatValue(2f)))),
                    listOf(FxConnection("source", "crt", "image")), "crt"),
                BuiltinFx.byId,
            )
        }
    }

    @Test fun acceptsLayeredPresetAndOrdersDependencies() {
        val plan = FxGraphValidator.validate(BuiltinPresets.all.getValue("audio-fire-overlay").graph, BuiltinFx.byId)
        assertEquals(listOf("flame", "over"), plan.orderedNodes.map { it.id })
    }

    @Test fun rejectsMissingInputsAndCycles() {
        val missing = FxGraph("missing", listOf(FxNode("water", "distort:water-refraction")), emptyList(), "water")
        assertTrue(assertFailsWith<FxGraphValidationException> {
            FxGraphValidator.validate(missing, BuiltinFx.byId)
        }.diagnostics.any { it.code == "missing-input" })

        val cycle = FxGraph(
            "cycle", listOf(FxNode("a", "display:crt"), FxNode("b", "display:lcd")),
            listOf(FxConnection("b", "a", "image"), FxConnection("a", "b", "image")), "b",
        )
        assertTrue(assertFailsWith<FxGraphValidationException> {
            FxGraphValidator.validate(cycle, BuiltinFx.byId)
        }.diagnostics.any { it.code == "cycle" })
    }

    @Test fun eventCursorDeduplicatesReusedSnapshotsAndResets() {
        val cursor = FxEventCursor()
        assertTrue(cursor.accept(10))
        assertTrue(!cursor.accept(10))
        assertTrue(!cursor.accept(9))
        assertTrue(cursor.accept(11))
        cursor.reset()
        assertTrue(cursor.accept(1))
    }

    @Test fun metadataSanitizesInvalidFloatsAndBoundsBands() {
        val meta = AudioFrameMetadata(3, rms = Float.NaN, peak = 5f, bands = listOf(Float.NEGATIVE_INFINITY, .5f), bpm = 800f, beatPhase = -.25f)
        assertEquals(0f, meta.rms)
        assertEquals(1f, meta.peak)
        assertEquals(listOf(0f, .5f), meta.bands)
        assertEquals(null, meta.bpm)
        assertEquals(.75f, meta.beatPhase)
    }

    @Test fun rotozoomAndKaleidoscopePresetsAreRegisteredAndValidate() {
        for (presetName in listOf("album-art-rotozoom", "kaleidoscope")) {
            val preset = BuiltinPresets.all.getValue(presetName)
            val plan = FxGraphValidator.validate(preset.graph, BuiltinFx.byId)
            assertEquals(1, plan.orderedNodes.size)
            assertEquals(presetName, preset.name)
        }
        assertTrue("distort:rotozoom" in BuiltinFx.byId)
        assertTrue("distort:kaleidoscope" in BuiltinFx.byId)
    }

    @Test fun fixedStepPlanBoundsCatchupAndResetsOnLongGaps() {
        val clock = FxFixedStepClock(stepSeconds = .01f, maxSteps = 3)
        assertEquals(0, clock.plan(0).steps)
        assertEquals(3, clock.plan(100_000_000).steps)
        assertTrue(clock.plan(1_000_000_000).reset)
        assertTrue(clock.plan(1_000_000_000).reset)
    }


    @Test fun lightningSpectrumDefaultsToCenteredNinetyPercentWidth() {
        val effect = BuiltinFx.byId.getValue("generator:lightning-spectrum")
        assertEquals(2, effect.version)
        val widthScale = effect.parameters.first { it.name == "widthScale" }
        assertEquals(.9f, (widthScale.defaultValue as FxParameterValue.FloatValue).value)
        assertEquals(.5f, widthScale.minimum)
        assertEquals(1f, widthScale.maximum)
    }

    @Test fun rotozoomVersionDeclaresOptionalTempoInput() {
        val rotozoom = BuiltinFx.byId.getValue("distort:rotozoom")
        assertEquals(3, rotozoom.version)
        assertTrue("audio.bpm" in rotozoom.metadataInterests)
    }
}
