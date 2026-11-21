# Documentation index

Current maturity: first source implementation with passing portable JVM and Android preset-codec tests, a successful local Android AAR/sample build, and glslang shader syntax validation. GPU-driver shader validation, visual output, device lifecycle, and performance still need device acceptance.

- [Architecture](architecture.md) — modules, graph execution, state ownership, and design limits.
- [API](api.md) — implemented Kotlin classes, ownership, thread rules, examples, and errors.
- [Rendering](rendering.md) — GLES requirements, targets, formats, sampling, alpha/color, and context lifecycle.
- [Effects](effects.md) — implemented descriptors, defaults, ranges, and limitations.
- [Simulation](simulation.md) — heat field, procedural water surface, stepping, and reset policy.
- [Audio metadata](audio-metadata.md) — units, events, sanitization, and host mapping.
- [Presets](presets.md) — schema-v1 JSON and the six example graphs.
- [Integration](integration.md) — source dependency setup and app-specific future integration boundaries.
- [Builds and testing](builds-and-testing.md) — exact commands and recorded evidence.
- [Compatibility](compatibility.md) — SDK/GLES support, budgets, schema compatibility, and unsupported targets.
- [Roadmap](roadmap.md) — P0/P1/P2 implementation and acceptance status.
- [Third-party provenance](third-party.md) — licenses and shader/asset origin.
- [Troubleshooting](troubleshooting.md) — common rendering failures and diagnostics.
- [Preset JSON files](../presets/) — runnable schema examples.
- [Repository instructions](../AGENTS.md), [README](../README.md), [changelog](../CHANGELOG.md), [MIT license](../LICENSE).
