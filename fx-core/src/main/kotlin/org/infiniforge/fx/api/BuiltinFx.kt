package org.infiniforge.fx.api

object BuiltinFx {
    private fun f(name: String, default: Float, min: Float, max: Float, units: String = "", description: String) =
        FxParameterDescriptor(name, FxParameterType.FLOAT, FxParameterValue.FloatValue(default), min, max, units, description)
    private fun i(name: String, default: Int, min: Int, max: Int, units: String = "", description: String) =
        FxParameterDescriptor(name, FxParameterType.INT, FxParameterValue.IntValue(default), min.toFloat(), max.toFloat(), units, description)
    private fun b(name: String, default: Boolean, description: String) =
        FxParameterDescriptor(name, FxParameterType.BOOLEAN, FxParameterValue.BoolValue(default), description = description)
    private fun e(name: String, default: String, values: List<String>, description: String) =
        FxParameterDescriptor(name, FxParameterType.ENUM, FxParameterValue.EnumValue(default), description = description, enumValues = values)

    val descriptors = listOf(
        FxEffectDescriptor(
            id = "core:passthrough", version = 1, title = "Pass through",
            description = "Copies the input image without visual processing.",
            category = FxEffectCategory.TRANSFORMER, inputPorts = listOf(FxInputPort("image")),
        ),
        FxEffectDescriptor(
            id = "display:crt", version = 1, title = "CRT classic",
            description = "Source-raster scanlines, a selectable phosphor mask and mild beam shaping.",
            category = FxEffectCategory.DISPLAY, inputPorts = listOf(FxInputPort("image")),
            parameters = listOf(
                f("scanlineStrength", .28f, 0f, .9f, description = "Darkening between source raster rows."),
                f("beamWidth", .72f, .2f, 1f, description = "Vertical beam fill within each source row."),
                f("maskStrength", .18f, 0f, .8f, description = "Output-pixel phosphor mask contrast."),
                f("maskPitch", 1f, .5f, 8f, "output pixels", "Phosphor mask repeat pitch."),
                e("mask", "shadow", listOf("none", "shadow", "aperture"), "Mask family."),
                f("curvature", .015f, 0f, .12f, description = "Stylized corner curvature."),
                f("vignette", .12f, 0f, .8f, description = "Edge darkening."),
            ),
        ),
        FxEffectDescriptor(
            id = "display:lcd", version = 1, title = "LCD handheld",
            description = "Logical source pixels, pixel fill and optional four-shade green mapping.",
            category = FxEffectCategory.DISPLAY, inputPorts = listOf(FxInputPort("image")),
            parameters = listOf(
                e("palette", "gameboy", listOf("source", "monochrome", "gameboy"), "Output palette; Game Boy mode is a stylized RGB luminance remap."),
                f("contrast", 1.08f, .25f, 2.5f, description = "Luminance contrast."),
                f("gamma", 1f, .4f, 2.5f, description = "Display gamma."),
                f("pixelGap", .06f, 0f, .35f, description = "Gap between logical source pixels."),
            ),
        ),
        FxEffectDescriptor(
            id = "distort:rotozoom", version = 4, title = "Album art rotozoom",
            description = "Continuously rotates and zooms an input image; valid BPM smoothly scales rotation speed within 0.5x–2x, with bounded RMS/onset accents.",
            category = FxEffectCategory.TRANSFORMER, inputPorts = listOf(FxInputPort("image")),
            metadataInterests = setOf("audio.rms", "audio.lowEnergy", "audio.onset", "audio.bpm"),
            parameters = listOf(
                f("zoom", 1.08f, .5f, 2.5f, description = "Base image scale around the center."),
                f("zoomPulse", .16f, 0f, .6f, description = "Autonomous zoom breathing amount."),
                f("rotationSpeed", 1.0f, -4f, 4f, "rad/s", "Base rotation speed at 120 BPM; smoothed BPM scales it from 0.5x to 2x and final speed is clamped to ±4 rad/s."),
                f("audioGain", .65f, 0f, 2f, description = "Optional RMS, bass and onset response; zero disables it."),
                e("edgeMode", "mirror", listOf("clamp", "repeat", "mirror"), "Sampling behavior outside the transformed image."),
            ),
        ),
        FxEffectDescriptor(
            id = "distort:kaleidoscope", version = 2, title = "Kaleidoscope",
            description = "Reflects and continuously rotates an input image into configurable radial symmetry sectors; optional BPM and audio energy modulate its rate.",
            category = FxEffectCategory.TRANSFORMER, inputPorts = listOf(FxInputPort("image")),
            metadataInterests = setOf("audio.rms", "audio.lowEnergy", "audio.onset", "audio.bpm"),
            parameters = listOf(
                i("segments", 8, 3, 16, "sectors", "Number of reflected radial sectors."),
                f("rotation", 0f, -3.141593f, 3.141593f, "radians", "Starting angular offset of the symmetry pattern."),
                f("rotationSpeed", .8f, -4f, 4f, "rad/s", "Continuous rotation speed at 120 BPM, smoothly scaled by BPM and bounded to ±4 rad/s."),
                f("audioGain", .55f, 0f, 2f, description = "Optional RMS, bass, and onset response; zero disables audio modulation."),
                f("zoom", 1f, .5f, 2f, description = "Scale of the sampled image around the pattern center."),
                e("edgeMode", "mirror", listOf("clamp", "repeat", "mirror"), "Sampling behavior outside the transformed image."),
            ),
        ),
        FxEffectDescriptor(
            id = "distort:water-refraction", version = 1, title = "Water refraction",
            description = "Animated multiscale procedural height field refracts the image beneath a water surface.",
            category = FxEffectCategory.TRANSFORMER, inputPorts = listOf(FxInputPort("image")),
            metadataInterests = setOf("audio.lowEnergy", "audio.onset"),
            parameters = listOf(
                f("depth", .42f, 0f, 1f, description = "Apparent water depth."),
                f("waveScale", 8f, 1f, 32f, "waves", "Spatial wave scale."),
                f("waveSpeed", .7f, 0f, 4f, "relative", "Autonomous animation speed."),
                f("roughness", .35f, 0f, 1f, description = "Multiscale choppy detail."),
                f("distortion", .012f, 0f, .08f, "UV", "Maximum bounded refraction displacement."),
                b("caustics", true, "Add a subtle, optional surface highlight."),
                f("audioGain", 0f, 0f, 2f, description = "Optional bass/onset modulation; zero disables audio response."),
            ),
        ),
        FxEffectDescriptor(
            id = "distort:heat-ripple", version = 1, title = "Heat ripple",
            description = "Own low-resolution heat simulation drives image refraction.",
            category = FxEffectCategory.TRANSFORMER, inputPorts = listOf(FxInputPort("image")),
            metadataInterests = setOf("audio.lowEnergy", "audio.onset"), needsHistory = true, predictablePassCount = 3,
            parameters = listOf(
                i("simulationSize", 128, 64, 512, "cells", "Square private heat-field resolution."),
                f("cooling", 1.8f, .1f, 8f, "s^-1", "Heat decay rate."),
                f("turbulence", .35f, 0f, 1f, description = "Bounded seeded lateral variation."),
                f("refraction", .018f, 0f, .08f, "UV", "Maximum image displacement."),
                f("audioGain", .6f, 0f, 3f, description = "Optional bass and onset injection gain."),
            ),
        ),
        FxEffectDescriptor(
            id = "generator:fire", version = 1, title = "Fire",
            description = "Independent private heat field rendered as a premultiplied transparent flame layer.",
            category = FxEffectCategory.GENERATOR, inputPorts = emptyList(),
            metadataInterests = setOf("audio.lowEnergy", "audio.onset"), needsHistory = true, predictablePassCount = 3,
            parameters = listOf(
                i("simulationSize", 128, 64, 512, "cells", "Square private heat-field resolution."),
                f("intensity", 1f, 0f, 2f, description = "Flame brightness and source strength."),
                f("cooling", 1.6f, .1f, 8f, "s^-1", "Heat decay rate."),
                f("audioGain", .8f, 0f, 3f, description = "Optional bass and onset injection gain."),
            ),
        ),
        FxEffectDescriptor(
            id = "generator:lightning-spectrum", version = 5, title = "Lightning spectrum",
            description = "Draws fast, hairline lightning traces with configurable side branches for 16 frequency bands on a transparent layer; optional fx.palette channels color each trace.",
            category = FxEffectCategory.GENERATOR, inputPorts = emptyList(),
            metadataInterests = setOf("audio.bands", "channel:fx.palette.colors"),
            parameters = listOf(
                f("gain", 1f, 0f, 5f, description = "Linear amplification applied to normalized spectrum bands."),
                f("maxHeight", 1f, .1f, 1f, description = "Maximum lightning height as a fraction of the full output height."),
                i("branchCount", 6, 1, 8, "tendrils", "Thin side tendrils per lightning trace; each tendril may form a short secondary twig."),
                f("animationSpeed", 20f, 0f, 40f, "x", "Procedural lightning evolution speed relative to real time."),
                f("glow", .9f, 0f, 2f, description = "Outline, electric core, and fork brightness."),
                f("idleHeight", .04f, 0f, .2f, description = "Small autonomous baseline used only when spectrum bands are absent."),
                f("widthScale", 1f, .5f, 1f, description = "Centered fraction of output width occupied by lightning; 1 fills the output width."),
            ),
        ),
        FxEffectDescriptor(
            id = "composite:blend", version = 1, title = "Blend layers",
            description = "Composites base and overlay branches in explicit alpha or additive mode.",
            category = FxEffectCategory.COMPOSITOR, inputPorts = listOf(FxInputPort("base"), FxInputPort("overlay")),
            parameters = listOf(
                e("mode", "alpha", listOf("alpha", "add"), "Layer blend rule."),
                f("opacity", 1f, 0f, 1f, description = "Overlay opacity."),
            ),
        ),
    )
    val byId: Map<String, FxEffectDescriptor> = descriptors.associateBy { it.id }
}
