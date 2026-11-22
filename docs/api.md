# Public API

Package root: org.infiniforge.fx.api. The GLES backend is org.infiniforge.fx.gles. This page describes implemented signatures and ownership.

## Frame and texture contracts

FxTexture(name, width, height, target, alphaMode, colorEncoding, sampling, uvRect, orientationDegrees) describes an existing nonzero texture name. The host owns creation/deletion and must keep it alive through render. Only TEXTURE_2D is accepted by the current backend. Frame geometry overrides the texture's uvRect/orientationDegrees for the primary source.

FxFrameInput(presentationTimeNanos, deltaTimeSeconds, frameIndex, geometry, sourceTexture, audio, channels, workingColorSpace, randomSeed) is one immutable-by-convention frame snapshot. The constructor rejects negative timestamps/frame indices, invalid delta, nonpositive dimensions, and source-size mismatch. presentationTimeNanos is a monotonic nanosecond render/presentation clock. Advance it when the host wants temporal effects to evolve, even when the image pixels are static. deltaTimeSeconds is bounded by each time-based effect; the water effect uses it for autonomous animation.

FxGeometry includes source/destination dimensions, positive pixelAspectRatio, orientation 0/90/180/270, contentRect and displayRect. FxRect uses normalized GL coordinates with origin at the lower-left. contentRect crops the source; orientation rotates it clockwise by the supplied angle in the shader mapping. displayRect places the result inside the target viewport. The engine does not decide emulator screen layout or fit policy.

FxRenderTarget(framebufferName, width, height, attachedTextureName, alphaMode, colorEncoding, viewport) is caller-owned. Framebuffer name 0 is allowed. Dimensions must equal frame destination size. Supplying attachedTextureName enables an early source/output alias check; always supply it when the attachment is known.

## Graph, descriptors, and values

- FxEffectDescriptor: stable namespaced ID and descriptor version, title/description/category, input ports, metadata interests, history/capability/pass-count hints, determinism flag, and typed parameter schema.
- FxNode: ID, effect ID, parameter overrides, and enabled flag.
- FxConnection(fromNodeId, toNodeId, inputPort): source-to-port edge. The source node ID is source.
- FxGraph(name, nodes, connections, outputNodeId): DAG with bounded node/edge counts. Output must be a sink; every node must contribute to it.
- FxParameterValue: FloatValue, IntValue, BoolValue, EnumValue, or VectorValue.
- FxPreset(name, description, graph, schemaVersion): preset schema version is not package version.
- BuiltinFx.descriptors / BuiltinFx.byId and BuiltinPresets.all expose the implemented registry.

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

Construct GlesFxEngine only after a GLES 3 context is current. render, resize, resetHistory, updateParameters, and close must use that same current context on the creating thread. No method switches contexts or schedules GL work. The caller owns the EGL lifecycle.

render returns FxRenderResult.Success or Failure(code, message, nodeId). Validation and device errors do not crash the host. Shader compile/link failures include the driver log. updateParameters applies partial typed values to a node after that graph has been rendered once. The override persists until the node leaves the active graph or changes effect ID; it does not mutate the graph/preset object.

resize requires positive dimensions within the queried maximum and resets effect histories. resetHistory(SEEK/TRACK_CHANGED/PAUSED/RESUMED/RESET) resets private state according to effect policy. CONTEXT_LOST abandons names without GL deletion and makes that engine unusable; create another engine after recreating the context. close must run while the original context is current.

## Preset JSON

FxPresetJson is in fx-gles because it uses Android's org.json. encode(FxPreset): String and decode(String): FxPreset support schema version 1 only. Size is limited to 256 Ki characters, node count 64, edge count 128, vector length 1–4. Decoding checks structure and schema; run FxGraphValidator against BuiltinFx.byId before execution. See [presets](presets.md).

## Lightning spectrum generator

`generator:lightning-spectrum` v5 is a no-input generator with optional `FxFrameInput.audio.bands`. It maps up to sixteen normalized bands from low to high frequency to lightning-column heights using linear gain. Defaults are edge-to-edge (`widthScale=1`, `maxHeight=1`), so there is no outer margin; the shader draws electric paths only, with no filled rectangular bar body. The bright spine and branches use screen-space antialiasing with a modestly wider approximately 1.4-pixel bright core at nominal scale. Six thin side tendrils per trace are drawn by default, each with a shorter secondary twig. Procedural shape/flicker motion runs at 20× real time by default. A present zero-valued band maps to zero height. When band data is missing, a small configurable idle baseline can keep a preview visible. The effect does not analyze audio or require source imagery; connect its output to the `overlay` input of `composite:blend` to layer it over an image.

A host may supply one to eight artwork palette colors through `FxFrameInput.channels`: `fx.palette.count` is an integer-valued float in 1–8, and each `fx.palette.color.<index>.r`, `.g`, and `.b` is an sRGB component in 0–1. Each lightning column selects a palette color in ascending slot order and wraps when it reaches the palette length. Missing channels use a cyan fallback. Palette extraction belongs to the host and is not performed on the render thread.

Descriptor parameters: `gain` (default 1, range 0–5; linear multiplier for normalized bands), `maxHeight` (default 1, range 0.1–1), `widthScale` (default 1, range 0.5–1), `branchCount` (default 6, integer range 1–8; thin side tendrils per trace, each with a shorter secondary twig), `animationSpeed` (default 20×, range 0–40; scales only procedural animation time), `glow` (default 0.9, range 0–2), and `idleHeight` (default 0.04, range 0–0.2). Line width is not user-adjustable; it is antialiased from output-pixel derivatives. Animation is clock-driven and reproducible for matching timestamps, seed, metadata, and backend within normal GPU precision limits. Descriptor v5 replaces v4 `boltWidth`; update saved graphs to use `branchCount`.

### Album-art rotozoom

`distort:rotozoom` descriptor version 4 integrates rotation as a phase, so changing tempo cannot rescale elapsed time and jump the image angle. BPM maps to a target multiplier in 0.5–2x, smoothed with a 0.9 s time constant. RMS/onset energy is smoothed over 0.18 s. The resulting angular rate is clamped to ±4 rad/s, and monotonic presentation timestamps drive rotation (the caller delta is a first-frame fallback); each step is capped at 100 ms. The default rate is 1.0 rad/s at 120 BPM. With missing BPM, the neutral 120 BPM target is used; missing audio otherwise leaves steady autonomous motion. Onset IDs remain deduplicated per effect instance.


### Kaleidoscope

`distort:kaleidoscope` descriptor version 2 starts with a 0 rad offset and continuously rotates at 0.8 rad/s by default. Its independent per-node phase advances from monotonic presentation timestamps with a 100 ms catch-up cap. Optional BPM scales the configured rate from 0.5x to 2x; RMS, low-band energy, and unique onset IDs provide bounded modulation when `audioGain` is nonzero. No audio is required for motion. Track/seek resets its phase; pause preserves phase and reinitializes the timestamp baseline.
