package org.infiniforge.fx.gles

import kotlin.math.exp

/** Smooth, bounded motion state for the audio-aware rotozoom runtime. */
internal class RotozoomMotion {
    private var elapsedSeconds = 0f
    private var rotationRadians = 0f
    private var tempoScale = 1f
    private var smoothedAudio = 0f

    fun advance(
        deltaSeconds: Float,
        bpm: Float?,
        energy: Float,
        onset: Float,
        baseRotationSpeed: Float,
        audioGain: Float,
    ): State {
        val dt = deltaSeconds.takeIf(Float::isFinite)?.coerceIn(0f, MAX_STEP_SECONDS) ?: 0f
        elapsedSeconds = (elapsedSeconds + dt) % TIME_WRAP_SECONDS

        val targetTempo = ((bpm ?: 120f) / 120f).coerceIn(MIN_TEMPO_SCALE, MAX_TEMPO_SCALE)
        tempoScale += (targetTempo - tempoScale) * smoothingAlpha(dt, TEMPO_SMOOTHING_SECONDS)

        val targetAudio = maxOf(energy.coerceIn(0f, 1f), onset.coerceIn(0f, 1f))
        smoothedAudio += (targetAudio - smoothedAudio) * smoothingAlpha(dt, AUDIO_SMOOTHING_SECONDS)
        val audioPulse = (smoothedAudio * audioGain.coerceIn(0f, MAX_AUDIO_GAIN)).coerceIn(0f, 1f)

        val rate = (baseRotationSpeed.coerceIn(-MAX_ROTATION_SPEED, MAX_ROTATION_SPEED) * tempoScale + audioPulse * AUDIO_ROTATION_RATE)
            .coerceIn(-MAX_ROTATION_SPEED, MAX_ROTATION_SPEED)
        rotationRadians = wrapRadians(rotationRadians + rate * dt)
        return State(elapsedSeconds, rotationRadians, audioPulse)
    }

    fun reset(preservePhase: Boolean = false) {
        if (!preservePhase) {
            elapsedSeconds = 0f
            rotationRadians = 0f
        }
        tempoScale = 1f
        smoothedAudio = 0f
    }

    data class State(val timeSeconds: Float, val rotationRadians: Float, val audioPulse: Float)

    private fun smoothingAlpha(dt: Float, timeConstant: Float): Float =
        if (dt <= 0f) 0f else (1f - exp(-dt / timeConstant)).coerceIn(0f, 1f)

    private fun wrapRadians(value: Float): Float {
        val twoPi = (2.0 * Math.PI).toFloat()
        return ((value % twoPi) + twoPi) % twoPi
    }

    private companion object {
        const val MAX_STEP_SECONDS = 0.1f
        const val TIME_WRAP_SECONDS = 600f
        const val MIN_TEMPO_SCALE = 0.5f
        const val MAX_TEMPO_SCALE = 2f
        const val TEMPO_SMOOTHING_SECONDS = 0.9f
        const val AUDIO_SMOOTHING_SECONDS = 0.18f
        const val MAX_ROTATION_SPEED = 4f
        const val MAX_AUDIO_GAIN = 2f
        const val AUDIO_ROTATION_RATE = 0.45f
    }
}
