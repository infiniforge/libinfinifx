package org.infiniforge.fx.gles

import kotlin.test.Test
import kotlin.test.assertTrue

class RotozoomMotionTest {
    @Test fun metadataSpeedChangesAreSmoothedAndPhaseRemainsContinuous() {
        val motion = RotozoomMotion()
        var state = motion.advance(
            deltaSeconds = 0.1f,
            bpm = 120f,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = 1f,
            audioGain = 0f,
        )
        val beforeChange = state.rotationRadians
        state = motion.advance(
            deltaSeconds = 1f / 60f,
            bpm = 240f,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = 1f,
            audioGain = 0f,
        )
        assertTrue(state.rotationRadians - beforeChange in 0f..0.03f)
    }

    @Test fun rotationRateAndAudioResponseStayBounded() {
        val motion = RotozoomMotion()
        val state = motion.advance(
            deltaSeconds = 1f,
            bpm = 400f,
            energy = 1f,
            onset = 1f,
            baseRotationSpeed = 100f,
            audioGain = 2f,
        )
        assertTrue(state.timeSeconds <= 0.1f)
        assertTrue(state.rotationRadians in 0f..0.40001f)
        assertTrue(state.audioPulse in 0f..1f)
    }
}
