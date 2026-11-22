package org.infiniforge.fx.gles

import kotlin.test.Test
import kotlin.test.assertTrue

class AnimatedRotationMotionTest {
    @Test fun autonomousDefaultRotationAdvancesWithoutAudio() {
        val motion = AnimatedRotationMotion()
        val state = motion.advance(
            presentationTimeNanos = 1_000_000_000L,
            deltaSeconds = 0.1f,
            bpm = null,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = .45f,
            audioGain = .55f,
        )
        assertTrue(state.rotationRadians in .044f..0.046f)
    }

    @Test fun metadataSpeedChangesAreSmoothedAndPhaseRemainsContinuous() {
        val motion = AnimatedRotationMotion()
        var state = motion.advance(
            presentationTimeNanos = 1_000_000_000L,
            deltaSeconds = 0.1f,
            bpm = 120f,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = 1f,
            audioGain = 0f,
        )
        val beforeChange = state.rotationRadians
        state = motion.advance(
            presentationTimeNanos = 1_016_666_667L,
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
        val motion = AnimatedRotationMotion()
        val state = motion.advance(
            presentationTimeNanos = 1_000_000_000L,
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

    @Test fun rotationUsesMonotonicFrameTimeWhenDeltaIsZero() {
        val motion = AnimatedRotationMotion()
        val first = motion.advance(
            presentationTimeNanos = 1_000_000_000L,
            deltaSeconds = 0f,
            bpm = null,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = 1f,
            audioGain = 0f,
        )
        val second = motion.advance(
            presentationTimeNanos = 1_100_000_000L,
            deltaSeconds = 0f,
            bpm = null,
            energy = 0f,
            onset = 0f,
            baseRotationSpeed = 1f,
            audioGain = 0f,
        )
        assertTrue(first.rotationRadians in 0f..0f)
        assertTrue(second.rotationRadians in .099f..0.101f)
    }
}
