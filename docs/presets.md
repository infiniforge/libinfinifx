# Presets and graph schema

Package version and preset schema version evolve independently. FxPreset.schemaVersion and JSON schemaVersion are currently 1. FxPresetJson in fx-gles encodes/decodes schema 1; another major schema is rejected with an explicit error. No migration helper exists yet. Unknown effect IDs/parameters are rejected by graph validation; the library does not silently substitute another effect.

## JSON shape

A document contains schemaVersion, preset name/description, and a graph. The graph contains a name, outputNodeId, nodes, and connections. A node stores id, effectId, enabled, and typed parameter values. Hardware handles, Android objects, absolute paths, and private effect state are never serialized.

Value records use type plus value:

- float: finite JSON number
- int: integer
- boolean: JSON boolean
- enum: stable string
- vector: one to four finite numbers

Connections use from, to, and input; source is the reserved input node ID. Every edge targets a declared port. Current bounds: 256 Ki characters, 64 nodes, 128 connections, vector length 1–4. After decode, call FxGraphValidator.validate with BuiltinFx.byId before render. Schema parsing does not itself prove that an effect is available on the device.

## Built-in registry

- neutral-pass-through — source to output, no effect node.
- crt-classic — source → display:crt → output.
- lcd-gameboy-dmg — source → display:lcd → output.
- water-over-image — source → distort:water-refraction → output.
- heat-haze — source → distort:heat-ripple → output.
- album-art-rotozoom — source → distort:rotozoom → output; optional energy/onset-driven zoom and rotation.
- kaleidoscope — source → distort:kaleidoscope → output.
- lightning-spectrum — optional `AudioFrameMetadata.bands` → centered `generator:lightning-spectrum` (90% width by default) → transparent output layer.
- audio-fire-overlay — source + generator:fire → composite:blend → output.

Examples are checked in under ../presets/. For example, [water-over-image.json](../presets/water-over-image.json) has no hardware-specific fields.

Disabled one-input nodes copy through; disabled generators produce a cleared transparent target; disabled blend nodes copy base. A no-node source-only graph is the explicit no-effect bypass. Graph nodes must contribute to the selected output. Cycles, missing input ports, unknown IDs, disconnected nodes, duplicate input edges, and output nodes that feed later nodes are invalid.

Built-in default values live with FxEffectDescriptor. A preset may store only parameter overrides. The descriptor version is independent from schemaVersion. The current codec rejects schema versions it does not know; future breaking changes require an explicit version bump and migration/rejection note.
