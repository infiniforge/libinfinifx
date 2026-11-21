package org.infiniforge.fx.gles

import org.infiniforge.fx.api.*

internal interface FxEffectRuntime : AutoCloseable {
    fun render(frame: FxFrameInput, inputs: Map<String, FxTexture>, output: FxRenderTarget, parameters: Map<String, FxParameterValue>)
    fun reset(reason: FxDiscontinuity)
    override fun close()
}

internal class BuiltinRuntime(
    private val effectId: String,
    private val passes: GlesPassRenderer,
    private val surfaces: SurfacePool,
) : FxEffectRuntime {
    private val heat: HeatSimulation? = if (effectId == "distort:heat-ripple" || effectId == "generator:fire") {
        HeatSimulation(passes, surfaces)
    } else null
    private var waterTimeSeconds = 0f
    private var rotozoomTimeSeconds = 0f
    private val rotozoomEventCursor = FxEventCursor()
    private var rotozoomEventTimestampNanos = -1L
    private var rotozoomEventStrength = 0f

    override fun render(
        frame: FxFrameInput,
        inputs: Map<String, FxTexture>,
        output: FxRenderTarget,
        parameters: Map<String, FxParameterValue>,
    ) {
        val image = inputs["image"]
        when (effectId) {
            "core:passthrough" -> passes.draw(FxShaders.COPY, output, listOf(image ?: error("Missing image input")), mapOf("uInput" to 0))
            "display:crt" -> {
                val p = parameters
                val mask = when (p.enum("mask")) { "shadow" -> 1; "aperture" -> 2; else -> 0 }
                passes.draw(FxShaders.CRT, output, listOf(image ?: error("Missing image input")), mapOf(
                    "uInput" to 0,
                    "uSourceSize" to floatArrayOf(frame.geometry.orientedSourceWidth.toFloat(), frame.geometry.orientedSourceHeight.toFloat()),
                    "uScanline" to p.float("scanlineStrength"),
                    "uBeamWidth" to p.float("beamWidth"),
                    "uMaskStrength" to p.float("maskStrength"),
                    "uMaskPitch" to p.float("maskPitch"),
                    "uMask" to mask,
                    "uCurvature" to p.float("curvature"),
                    "uVignette" to p.float("vignette"),
                ))
            }
            "display:lcd" -> {
                val p = parameters
                val palette = when (p.enum("palette")) { "monochrome" -> 1; "gameboy" -> 2; else -> 0 }
                passes.draw(FxShaders.LCD, output, listOf(image ?: error("Missing image input")), mapOf(
                    "uInput" to 0,
                    "uSourceSize" to floatArrayOf(frame.geometry.orientedSourceWidth.toFloat(), frame.geometry.orientedSourceHeight.toFloat()),
                    "uPalette" to palette,
                    "uContrast" to p.float("contrast"),
                    "uGamma" to p.float("gamma"),
                    "uPixelGap" to p.float("pixelGap"),
                ))
            }
            "distort:rotozoom" -> {
                val p = parameters
                rotozoomTimeSeconds = (rotozoomTimeSeconds + frame.deltaTimeSeconds.coerceIn(0f, 0.1f)) % 600f
                frame.audio?.onsetEvents.orEmpty().forEach { event ->
                    if (rotozoomEventCursor.accept(event.id)) {
                        rotozoomEventTimestampNanos = event.timestampNanos
                        rotozoomEventStrength = event.strength
                    }
                }
                val eventAge = if (rotozoomEventTimestampNanos < 0L) Float.POSITIVE_INFINITY else
                    (frame.presentationTimeNanos - rotozoomEventTimestampNanos).coerceAtLeast(0L) / 1_000_000_000f
                val onsetPulse = if (eventAge <= 0.45f) rotozoomEventStrength * kotlin.math.exp(-eventAge * 7.5f) else 0f
                val energy = maxOf(frame.audio?.rms ?: 0f, (frame.audio?.lowEnergy ?: 0f) * 0.85f)
                val audioPulse = (maxOf(onsetPulse, energy * 0.55f) * p.float("audioGain")).coerceIn(0f, 1f)
                val edge = when (p.enum("edgeMode")) { "repeat" -> 1; "mirror" -> 2; else -> 0 }
                passes.draw(FxShaders.ROTOZOOM, output, listOf(image ?: error("Missing image input")), mapOf(
                    "uInput" to 0,
                    "uTime" to rotozoomTimeSeconds,
                    "uZoom" to p.float("zoom"),
                    "uZoomPulse" to p.float("zoomPulse"),
                    "uRotationSpeed" to p.float("rotationSpeed"),
                    "uAudioPulse" to audioPulse,
                    "uEdgeMode" to edge,
                ))
            }
            "distort:kaleidoscope" -> {
                val p = parameters
                val edge = when (p.enum("edgeMode")) { "repeat" -> 1; "mirror" -> 2; else -> 0 }
                passes.draw(FxShaders.KALEIDOSCOPE, output, listOf(image ?: error("Missing image input")), mapOf(
                    "uInput" to 0,
                    "uSegments" to p.int("segments"),
                    "uRotation" to p.float("rotation"),
                    "uZoom" to p.float("zoom"),
                    "uEdgeMode" to edge,
                ))
            }
            "distort:water-refraction" -> {
                val p = parameters
                val audio = frame.audio
                val age = audio?.onsetEvents.orEmpty().maxOfOrNull {
                    ((frame.presentationTimeNanos - it.timestampNanos).coerceAtLeast(0L) / 1_000_000_000f)
                        .let { seconds -> if (seconds <= 0.35f) it.strength * kotlin.math.exp(-seconds * 7f) else 0f }
                } ?: 0f
                val energy = ((audio?.lowEnergy ?: 0f) * 0.55f + age * 0.45f) * p.float("audioGain")
                waterTimeSeconds = (waterTimeSeconds + frame.deltaTimeSeconds.coerceIn(0f, 0.1f) * p.float("waveSpeed")) % 600f
                val time = waterTimeSeconds
                passes.draw(FxShaders.WATER, output, listOf(image ?: error("Missing image input")), mapOf(
                    "uInput" to 0,
                    "uInputSize" to floatArrayOf(frame.geometry.orientedSourceWidth.toFloat(), frame.geometry.orientedSourceHeight.toFloat()),
                    "uTime" to time,
                    "uDepth" to p.float("depth"),
                    "uScale" to p.float("waveScale"),
                    "uRoughness" to p.float("roughness"),
                    "uDistortion" to p.float("distortion"),
                    "uCaustics" to if (p.bool("caustics")) 1 else 0,
                    "uAudio" to energy.coerceIn(0f, 1f),
                ))
            }
            "distort:heat-ripple" -> {
                val sim = heat ?: error("Heat simulation was not prepared")
                sim.render(frame, inputs, output, parameters, fire = false)
            }
            "generator:fire" -> {
                val sim = heat ?: error("Fire simulation was not prepared")
                sim.render(frame, inputs, output, parameters, fire = true)
            }
            "composite:blend" -> {
                val p = parameters
                val base = inputs["base"] ?: error("Missing base input")
                val overlay = inputs["overlay"] ?: error("Missing overlay input")
                passes.draw(FxShaders.BLEND, output, listOf(base, overlay), mapOf(
                    "uBase" to 0, "uOverlay" to 1,
                    "uOpacity" to p.float("opacity"),
                    "uMode" to if (p.enum("mode") == "add") 1 else 0,
                ))
            }
            else -> error("No GLES runtime is registered for '$effectId'")
        }
    }

    override fun reset(reason: FxDiscontinuity) {
        heat?.reset(reason)
        rotozoomEventCursor.reset()
        rotozoomEventTimestampNanos = -1L
        rotozoomEventStrength = 0f
        if (reason != FxDiscontinuity.PAUSED) {
            waterTimeSeconds = 0f
            rotozoomTimeSeconds = 0f
        }
    }
    override fun close() { heat?.close() }
}

private class HeatSimulation(
    private val passes: GlesPassRenderer,
    private val pool: SurfacePool,
) : AutoCloseable {
    private var size = 0
    private var read: FxSurface? = null
    private var write: FxSurface? = null
    private val clock = FxFixedStepClock(stepSeconds = 1f / 60f, maxSteps = 4, discontinuitySeconds = 0.5f)
    private val eventCursor = FxEventCursor()

    fun render(
        frame: FxFrameInput,
        inputs: Map<String, FxTexture>,
        output: FxRenderTarget,
        parameters: Map<String, FxParameterValue>,
        fire: Boolean,
    ) {
        val requestedSize = parameters.int("simulationSize")
        ensureSize(requestedSize)
        if (read == null || write == null) error("Heat texture allocation failed")
        val plan = clock.plan(frame.presentationTimeNanos)
        if (plan.reset) clear()
        val cooling = parameters.float("cooling")
        val audioGain = parameters.float("audioGain")
        val onset = consumeOnsets(frame, audioGain)
        val energy = (frame.audio?.lowEnergy ?: 0f) * audioGain
        val seedBits = frame.randomSeed xor (frame.randomSeed ushr 32)
        val seed = (seedBits and 0xffffL).toFloat() / 65535f
        if (!plan.reset) {
            repeat(plan.steps) { step ->
                val currentRead = read ?: error("Heat read texture unavailable")
                val currentWrite = write ?: error("Heat write texture unavailable")
                val burst = if (step == 0) onset else 0f
                passes.draw(
                    FxShaders.HEAT_STEP, currentWrite.target(), listOf(currentRead.input()), mapOf(
                        "uField" to 0,
                        "uDt" to plan.stepSeconds,
                        "uCooling" to cooling,
                        "uEnergy" to energy.coerceIn(0f, 1f),
                        "uBurst" to burst,
                        "uTurbulence" to parameters["turbulence"].let { (it as? FxParameterValue.FloatValue)?.value ?: 0.18f },
                        "uSeed" to seed,
                        "uTime" to ((frame.presentationTimeNanos % 600_000_000_000L) / 1_000_000_000f),
                    ),
                )
                val oldRead = read
                read = write
                write = oldRead
            }
        }
        val field = read?.input() ?: error("Heat texture unavailable")
        if (fire) {
            passes.draw(FxShaders.FIRE, output, listOf(field), mapOf(
                "uField" to 0,
                "uIntensity" to parameters.float("intensity"),
            ))
        } else {
            val image = inputs["image"] ?: error("heat-ripple requires an input texture")
            passes.draw(FxShaders.HEAT_RIPPLE, output, listOf(image, field), mapOf(
                "uInput" to 0,
                "uField" to 1,
                "uFieldSize" to floatArrayOf(size.toFloat(), size.toFloat()),
                "uRefraction" to parameters.float("refraction"),
            ))
        }
    }

    fun reset(reason: FxDiscontinuity) {
        clock.reset()
        eventCursor.reset()
        clear()
    }

    override fun close() {
        read?.let(pool::release)
        write?.let(pool::release)
        read = null
        write = null
        size = 0
    }

    private fun ensureSize(next: Int) {
        if (size == next && read != null && write != null) return
        close()
        size = next
        read = pool.acquire(next, next)
        write = pool.acquire(next, next)
        clear()
        clock.reset()
    }

    private fun clear() {
        read?.let { passes.clear(it.target(), 0f, .5f, .5f, 0f) }
        write?.let { passes.clear(it.target(), 0f, .5f, .5f, 0f) }
    }

    private fun consumeOnsets(frame: FxFrameInput, gain: Float): Float {
        var burst = 0f
        frame.audio?.onsetEvents.orEmpty().forEach { event ->
            if (eventCursor.accept(event.id)) {
                val age = frame.presentationTimeNanos - event.timestampNanos
                if (age in 0..250_000_000L) burst = maxOf(burst, event.strength * gain)
            }
        }
        return burst.coerceIn(0f, 1f)
    }
}

private fun Map<String, FxParameterValue>.float(name: String) =
    (get(name) as? FxParameterValue.FloatValue)?.value ?: error("Missing float parameter '$name'")
private fun Map<String, FxParameterValue>.int(name: String) =
    (get(name) as? FxParameterValue.IntValue)?.value ?: error("Missing int parameter '$name'")
private fun Map<String, FxParameterValue>.bool(name: String) =
    (get(name) as? FxParameterValue.BoolValue)?.value ?: error("Missing boolean parameter '$name'")
private fun Map<String, FxParameterValue>.enum(name: String) =
    (get(name) as? FxParameterValue.EnumValue)?.value ?: error("Missing enum parameter '$name'")
