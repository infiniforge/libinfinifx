# Public API

Package root: org.infiniforge.fx.api. The GLES backend is org.infiniforge.fx.gles. This page describes implemented signatures and ownership.

## Frame and texture contracts

FxTexture(name, width, height, target, alphaMode, colorEncoding, sampling, uvRect, orientationDegrees) describes an existing nonzero texture name. The host owns creation/deletion and must keep it alive through render. Only TEXTURE_2D is accepted by the current backend. Frame geometry overrides the texture's uvRect/orientationDegrees for the primary source.

FxFrameInput(presentationTimeNanos, deltaTimeSeconds, frameIndex, geometry, sourceTexture, audio, channels, workingColorSpace, randomSeed, text) is one immutable-by-convention frame snapshot. The constructor rejects negative timestamps/frame indices, invalid delta, nonpositive dimensions, and source-size mismatch. presentationTimeNanos is a monotonic nanosecond render/presentation clock. Advance it when the host wants temporal effects to evolve, even when the image pixels are static. deltaTimeSeconds is bounded by each time-based effect; the water effect uses it for autonomous animation.

`FxTextInput(value, positionSeconds, durationSeconds)` is optional host-supplied text and item timeline data for text-aware generators. `generator:lightning-spectrum` consumes up to 16 normalized audio bands from the same immutable `FxFrameInput.audio` snapshot; no live analyzer or subscription is created. It contains no player, view, bitmap, or Android UI state. Hosts choose and supply the current source data through the independent `sourceTexture`, `text`, `audio`, and namespaced `channels` frame fields; InfiniFX owns processing and rendering.

FxGeometry includes source/destination dimensions, positive pixelAspectRatio, orientation 0/90/180/270, contentRect and displayRect. FxRect uses normalized GL coordinates with origin at the lower-left. contentRect crops the source; orientation rotates it clockwise by the supplied angle in the shader mapping. displayRect places the result inside the target viewport. The engine does not decide emulator screen layout or fit policy.

FxRenderTarget(framebufferName, width, height, attachedTextureName, alphaMode, colorEncoding, viewport) is caller-owned. Framebuffer name 0 is allowed. Dimensions must equal frame destination size. Supplying attachedTextureName enables an early source/output alias check; always supply it when the attachment is known.

## Graph, descriptors, and values

- FxEffectDescriptor: stable namespaced ID and descriptor version, title/description/category, input ports, metadata interests, history/capability/pass-count hints, determinism flag, and typed parameter schema.
- FxNode: ID, effect ID, parameter overrides, and enabled flag.
- FxConnection(fromNodeId, toNodeId, inputPort): source-to-port edge. The source node ID is source.
- FxGraph(name, nodes, connections, outputNodeId): DAG with bounded node/edge counts. Output must be a sink; every node must contribute to it.
- FxParameterValue: FloatValue, IntValue, BoolValue, EnumValue, or VectorValue.
- FxPreset(name, description, graph, schemaVersion): preset schema version is not package version.
- `BuiltinFx.descriptors` / `BuiltinFx.byId` expose all built-ins. `BuiltinFx.availableFilters` lists transformer/display effects, and `BuiltinFx.availableGenerators` lists generators; both derive from the central descriptor registry. `BuiltinPresets.all` exposes the preset registry.

FxGraphValidator.validate(graph, descriptors) returns FxExecutionPlan or throws FxGraphValidationException with diagnostics. validateParameters(descriptor, values) fills omitted values from defaults and rejects unknown names, wrong types, non-finite values, or values outside declared ranges.

FxEventCursor accepts each monotonically increasing host event ID once and can be reset at a track/seek boundary. Built-in heat/fire nodes use an independent cursor per node.

## Engine lifecycle

    val engine = GlesFxEngine(renderTargetBudgetBytes = 128L * 1024L * 1024L)
    val frame = FxFrameInput(
        presentationTimeNanos = monotonicNanos,
        deltaTimeSeconds = deltaSeconds,
        frameIndex = frameNumber,
        geometry = FxGeometry(sourceW, sourceH, outputW, outputH),
        sourceTexture = FxTexture(textureName, sourceW, sourceH),
        audio = optionalAudioSnapshot,
        randomSeed = seed,
    )
    val result = engine.render(BuiltinPresets.all.getValue("water-over-image").graph,
        frame, FxRenderTarget(framebufferName, outputW, outputH, attachedTextureName))
    engine.resize(outputW, outputH)
    engine.resetHistory(FxDiscontinuity.SEEK)
    engine.close()

The `lightning-spectrum` preset is a source-independent transparent generator. Blend its output over an image with `composite:blend`; its `gain`, `maxHeight`, `barGap`, `glow`, and `idleHeight` parameters can be edited at runtime or stored in JSON.

For a text generator, supply text and the selected item's timeline in the frame:

    val frame = FxFrameInput(
        presentationTimeNanos = monotonicNanos,
        deltaTimeSeconds = deltaSeconds,
        frameIndex = frameNumber,
        geometry = FxGeometry(1, 1, outputW, outputH),
        text = FxTextInput("The Meditator", positionSeconds = itemPosition, durationSeconds = itemDuration),
    )

Render the `text-scroller` preset to the caller's transparent target. Supplying item duration enables the end title window; without it, the title appears only at the start.

Construct GlesFxEngine only after a GLES 3 context is current. render, resize, resetHistory, updateParameters, and close must use that same current context on the creating thread. No method switches contexts or schedules GL work. The caller owns the EGL lifecycle.

render returns FxRenderResult.Success or Failure(code, message, nodeId). Validation and device errors do not crash the host. Shader compile/link failures include the driver log. updateParameters applies partial typed values to a node after that graph has been rendered once. The override persists until the node leaves the active graph or changes effect ID; it does not mutate the graph/preset object.

resize requires positive dimensions within the queried maximum and resets effect histories. resetHistory(SEEK/TRACK_CHANGED/PAUSED/RESUMED/RESET) resets private state according to effect policy. CONTEXT_LOST abandons names without GL deletion and makes that engine unusable; create another engine after recreating the context. close must run while the original context is current.

## Preset JSON

FxPresetJson is in fx-gles because it uses Android's org.json. encode(FxPreset): String and decode(String): FxPreset support schema version 1 only. Size is limited to 256 Ki characters, node count 64, edge count 128, vector length 1–4. Decoding checks structure and schema; run FxGraphValidator against BuiltinFx.byId before execution. See [presets](presets.md).

### Lightning spectrum

`generator:lightning-spectrum` is a no-input generator with optional
`FxFrameInput.audio.bands`. It maps up to sixteen normalized bands from low to
high frequency into columns spanning the entire destination width. The shader
adds a colored bar body, an animated jagged lightning core, short forks, and a
glow halo, then emits a transparent premultiplied layer. Missing or silent
bands leave a small configurable idle baseline so a preview remains visible.
The effect does not analyze audio or need source imagery; when composing over an
image, connect its output to the `overlay` input of `composite:blend`.

Its descriptor parameters are `gain` (default 2.6, range 0–5), `maxHeight`
(default 0.88, range 0.1–1), `barGap` (default 0.14, range 0–0.4), `glow`
(default 0.9, range 0–2), and `idleHeight` (default 0.04, range 0–0.2). All
ranges are normalized output fractions except gain and glow. Animation is
clock-driven and reproducible for matching timestamps, seed, metadata, and
backend within normal GPU precision limits.
