# Simulation and field semantics

Simulation state belongs to a graph node runtime. Two heat-ripple/fire nodes receive separate RGBA8 texture pairs and independent clocks even when they use the same effect ID. There is no shared heat singleton, wind field, lightning field, or cross-effect mutation API.

## Heat field

The simulation uses two private RGBA8 textures at a square 64–512 resolution (default 128). Channels are:

- R: nonnegative temperature/energy in [0,1].
- G: encoded horizontal velocity, where .5 is zero and values near 0/1 encode bounded negative/positive drift.
- B: encoded vertical velocity with the same .5 neutral point; positive means upward in GL coordinates.
- A: bounded density proxy, currently equal to temperature.

Each fixed step uses a stable advection/cooling approximation:

    advected = sample(previousField, uv - velocity * 0.12)
    temperature = max(0, advected.temperature * exp(-cooling * dt)
                         + (bottomSource + onsetBurst) * dt)
    velocityX = clamp(dampedVelocityX + seededNoise * turbulence * dt, -0.08, 0.08)
    velocityY = clamp(dampedVelocityY + temperature * buoyancy * dt, -0.02, 0.16)

The source is a bounded band at the lower edge with seeded variation across vents. Heat is advected upward, cools exponentially, and clamps to finite RGBA8 ranges. The current implementation has no explicit diffusion solver or smoke channel; the A channel is reserved for a future density field. Edges clamp to the field boundary.

Steps are 1/60 second. Fractional time is accumulated; at most four steps run per render. A backwards/repeated timestamp resets history. A gap above 500 ms clears the field and skips catch-up instead of integrating a large delta. Hosts should supply a monotonic presentation/render clock. SEEK, TRACK_CHANGED, PAUSED, RESUMED, SOURCE_RESIZED, and RESET can be delivered through resetHistory.

Audio lowEnergy raises the base source by audioGain. A unique onset event ID no older than 250 ms injects a bounded one-step burst. Each node runtime uses its own FxEventCursor, so reusing one metadata snapshot does not retrigger it each render; resetHistory clears that cursor. Missing audio means autonomous low-level source injection; it does not disable the effect.

Heat-ripple samples neighboring R values and uses the central difference as a UV displacement vector. It refracts the connected image input; missing image input is an error, never a false-color replacement. Fire maps the same private numerical field to a transparent warm palette. It does not depend on a heat-ripple or another fire node.

## Water surface

Water refraction currently builds an analytic 2D height field in the fragment shader instead of retaining a wave-equation texture. Several oriented waves at different scales and speeds combine with a multiplied high-frequency roughness term. Neighbor samples estimate the height gradient; bounded gradient displacement samples the image underneath the surface. Depth, scale, roughness, speed and distortion are independent parameters. Edge sampling clamps to the image. Caustic-style light is optional and low intensity.

The water field is autonomous: wave phase advances from deltaTimeSeconds, capped to 100 ms per draw. Missing audio is normal. Optional lowEnergy/onset modulation is multiplied by audioGain, whose default zero disables audio response. This is a procedural approximation, not a physical fluid model or a blue-tinted overlay. A damped ping-pong wave equation is a future implementation behind the same water effect ID.

## Determinism and limits

Given the same frame timestamps/deltas, metadata snapshots, randomSeed, graph, parameters, and backend, procedural fields are repeatable within device floating-point differences. No bit-identical output across GPU vendors is promised. Simulation targets count against the engine's bounded RGBA8 allocation budget. Raising simulationSize to 512 uses two 512-square textures per node; multiple nodes multiply that cost.
