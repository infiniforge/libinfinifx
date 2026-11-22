# libinfinifx

A standalone Android library for composable GLES 3.0 image effects, simulation fields, and texture compositing. It owns shader passes and per-node GPU state. Host apps own input textures, EGL lifecycle, media clocks, audio analysis, display layout, file formats, and UI.

## Initial implementation status

The repository contains a portable Kotlin API/graph validator and a GLES 3.0 Android backend. Built-ins include pass-through, CRT, LCD, album-art rotozoom, kaleidoscope, procedural water refraction, a private heat-field simulation with heat ripple and fire rendering, a full-output lightning-spectrum generator with optional host-supplied palette channels, and alpha/additive layer composition. Nine JSON examples and a runnable Android sample are included.

Recorded baseline evidence from the prior 0.1.0 revision includes passing portable graph/metadata tests, local GLES AAR/sample APK assembly, and glslang shader syntax validation. The 2026-11-21 working revision passes 26 portable test executions, Android AAR/sample assembly, and GLSL syntax validation for full-output lightning geometry, zero-height bands, optional palette channels, and tempo-smoothed rotozoom; the Infinimedia host adapter is a separate checkout and still needs its own build. Device visuals remain unverified. GPU-driver compilation, visual correctness, device lifecycle, and performance are **not verified on device**. See [roadmap](docs/roadmap.md) and [build evidence](docs/builds-and-testing.md).

## Standalone build

Requirements: Git, JDK 17 or 21, Gradle wrapper support, Android SDK platform 36 and build tools 35.0.0. Android library minimum API is 26. GLES 3.0 is required. The wrapper pins Gradle 9.7.1 with SHA-256; AGP is 8.9.2 and Kotlin is 2.2.20. No InfiniForge app or sibling checkout is required.

Run the portable test suite without Android SDK:

    ./gradlew :fx-core:test --max-workers=1 --console=plain

Optionally validate the built-in GLSL source with `glslangValidator` installed on PATH:

    python3 scripts/validate-shaders.py

Build the Android AAR and sample APK with SDK 36 configured through ANDROID_HOME or an untracked local.properties:

    ./build.sh --max-workers=1 --console=plain

This Termux checkout was verified with its SDK under `$HOME/android-sdk`:

    ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain

If Termux's Gradle setup needs its installed aapt2, add `-Pandroid.aapt2FromMavenOverride="$(command -v aapt2)"` to the Gradle invocation. The build wrapper accepts and forwards normal Gradle options to both stages.

The AAR is under fx-gles/build/outputs/aar/; the sample APK is under sample/build/outputs/apk/debug/. Exact environment evidence is in [builds and testing](docs/builds-and-testing.md).

## Quick start

Create and use the engine on the render thread after an OpenGL ES 3 context is current. Input texture and output framebuffer remain host-owned.

    val engine = GlesFxEngine()
    val preset = BuiltinPresets.all.getValue("crt-classic")
    val frame = FxFrameInput(
        presentationTimeNanos = frameClockNanos,
        deltaTimeSeconds = frameDeltaSeconds,
        frameIndex = frameNumber,
        geometry = FxGeometry(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            destinationWidth = outputWidth,
            destinationHeight = outputHeight,
        ),
        sourceTexture = FxTexture(
            name = sourceTextureId,
            width = sourceWidth,
            height = sourceHeight,
            sampling = FxSampling.NEAREST,
        ),
    )
    val result = engine.render(preset.graph, frame, FxRenderTarget(outputFramebuffer, outputWidth, outputHeight))
    if (result is FxRenderResult.Failure) {
        Log.e("FX", result.message)
    }
    engine.close()

See [API](docs/api.md) for exact lifecycle and error behavior. The sample Activity provides preset selection, synthetic audio toggling, and a generated asymmetric quadrant/checker fixture.

## Built-in presets

- neutral-pass-through
- crt-classic
- lcd-gameboy-dmg
- water-over-image
- heat-haze
- audio-fire-overlay
- album-art-rotozoom
- kaleidoscope

Preset JSON is versioned independently from the library package. See [preset schema and examples](docs/presets.md).

## Boundaries and limits

- No app source dependency; no audio decoding or analysis; no image/model/media codecs.
- No global mutable effect state. Each heat/fire graph node owns its own heat field.
- GLES 3.0 only. External OES textures require a host copy adapter and currently return an explicit unsupported-target result.
- RGBA8 internal targets and a 128 MiB default pool budget; no float render-target dependency, CPU readback, Vulkan, AGSL, phosphor history, or LCD ghosting yet.
- The Game Boy palette is a stylized four-shade luminance remap. It cannot recover original indexed palette IDs from RGB input.
- CRT presets are artistic approximations, not claims about specific hardware measurements.
- Maven artifacts have not been published. Consumers should pin the library source commit and use Gradle composite substitution; see [integration](docs/integration.md).

## Documentation

Start at [docs/README.md](docs/README.md). It links architecture, API, rendering, effects, simulation, audio metadata, presets, app integration, compatibility, testing, roadmap, troubleshooting, and third-party provenance.


## Lightning-spectrum generator

`generator:lightning-spectrum` is an original GLES generator that draws only
lightning paths across the full output width and height by default; it does not
draw filled rectangular bars. Its core strokes use screen-space antialiasing at
roughly 1.4 output pixels at nominal scale. Six thin side tendrils and a short secondary twig per
tendril are drawn for each frequency trace by default. It reads optional normalized
`AudioFrameMetadata.bands`, makes present zero-valued bands exactly zero height,
and emits an optional idle preview only when bands are missing. Hosts may send
up to eight sRGB colors through `FxFrameInput.channels` using
`fx.palette.count` and `fx.palette.color.<index>.{r,g,b}` channels. Bands select
these colors in order and wrap. Missing palette channels use a cyan fallback.
Hosts can adjust width, linear gain, height, tendril count, animation speed, glow,
and idle baseline through graph parameters. The default animation evolves at
20× real time. Use the `lightning-spectrum` preset directly or alpha-blend
its output over an image. Shader-driver and device visual acceptance still need
a GLES device.
