# Documentation index

Current maturity: source implementation includes pass-through, compositing, CRT/LCD, water/heat, fire, rotozoom, kaleidoscope, a host-text scroller, and the full-width audio lightning-spectrum generator. Built-in filters and generators are discoverable through fx-core. The current source compiles and assembles the GLES AAR and sample APK; the lightning-spectrum shader still needs source-level syntax and device visual acceptance. GPU-driver shader validation, device lifecycle, and performance need device acceptance.

- [Architecture](architecture.md) — modules, graph execution, state ownership, and design limits.
- [API](api.md) — implemented Kotlin classes, ownership, thread rules, examples, and errors.
- [Rendering](rendering.md) — GLES requirements, targets, formats, sampling, alpha/color, and context lifecycle.
- [Effects](effects.md) — implemented descriptors, defaults, ranges, and limitations.
- [Simulation](simulation.md) — heat field, procedural water surface, stepping, and reset policy.
- [Audio metadata](audio-metadata.md) — units, events, sanitization, and host mapping.
- [Presets](presets.md) — schema-v1 JSON and the ten example graphs.
- [Integration](integration.md) — source dependency setup and app-specific future integration boundaries.
- [Builds and testing](builds-and-testing.md) — exact commands, shader syntax helper, and recorded evidence.
- [Compatibility](compatibility.md) — SDK/GLES support, budgets, schema compatibility, and unsupported targets.
- [Roadmap](roadmap.md) — P0/P1/P2 implementation and acceptance status.
- [Third-party provenance](third-party.md) — licenses and shader/asset origin.
- [Troubleshooting](troubleshooting.md) — common rendering failures and diagnostics.
- [Preset JSON files](../presets/) — runnable schema examples.
- [Repository instructions](../AGENTS.md), [README](../README.md), [changelog](../CHANGELOG.md), [MIT license](../LICENSE).

The `generator:lightning-spectrum` built-in adds a full-width audio-band graph
with a procedural colored lightning treatment; see [effects](effects.md),
[API](api.md), and [presets](presets.md).
