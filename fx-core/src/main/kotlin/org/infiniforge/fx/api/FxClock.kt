package org.infiniforge.fx.api

/** Fixed-step plan with bounded catch-up. Gaps over 500 ms reset history instead of integrating a pause. */
data class FxStepPlan(val steps: Int, val stepSeconds: Float, val reset: Boolean, val droppedSeconds: Float)

class FxFixedStepClock(
    private val stepSeconds: Float = 1f / 60f,
    private val maxSteps: Int = 4,
    private val discontinuitySeconds: Float = 0.5f,
) {
    private var lastTimeNanos: Long? = null
    private var remainder = 0f
    init { require(stepSeconds > 0f && maxSteps > 0 && discontinuitySeconds > 0f) }

    fun plan(timestampNanos: Long): FxStepPlan {
        require(timestampNanos >= 0)
        val previous = lastTimeNanos
        lastTimeNanos = timestampNanos
        if (previous == null) return FxStepPlan(0, stepSeconds, reset = false, droppedSeconds = 0f)
        if (timestampNanos <= previous) {
            remainder = 0f
            return FxStepPlan(0, stepSeconds, reset = true, droppedSeconds = 0f)
        }
        val elapsed = ((timestampNanos - previous) / 1_000_000_000.0).toFloat()
        if (!elapsed.isFinite() || elapsed > discontinuitySeconds) {
            remainder = 0f
            return FxStepPlan(0, stepSeconds, reset = true, droppedSeconds = elapsed.coerceAtLeast(0f))
        }
        remainder += elapsed
        val wanted = kotlin.math.floor(remainder / stepSeconds).toInt()
        val steps = wanted.coerceAtMost(maxSteps)
        val dropped = (wanted - steps) * stepSeconds
        remainder -= wanted * stepSeconds
        return FxStepPlan(steps, stepSeconds, reset = false, droppedSeconds = dropped)
    }

    fun reset() { lastTimeNanos = null; remainder = 0f }
}
