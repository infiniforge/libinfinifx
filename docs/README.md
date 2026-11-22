# Documentation index

Current maturity: source implementation includes pass-through, compositing, CRT/LCD, water/heat, fire, rotozoom, kaleidoscope, and the full-output audio lightning-spectrum generator with optional palette channels. The current source includes full-output lightning without filled bar boxes, optional host-supplied palette channels, 20× animation, modestly widened antialiased lines (about 1.4 output pixels at nominal scale) with six tendrils and short twigs by default, exact zero-height band behavior, and tempo-smoothed, bounded rotozoom motion. Portable tests, GLSL syntax validation, and Android AAR/sample builds pass for these updates; GPU-driver shader validation, visual output, device lifecycle, and performance still need device acceptance.

- [Architecture](architecture.md) — modules, graph execution, state ownership, and design limits.
- [API](api.md) — implemented Kotlin classes, ownership, thread rules, examples, and errors.
- [Rendering](rendering.md) — GLES requirements, targets, formats, sampling, alpha/color, and context lifecycle.
- [Effects](effects.md) — implemented descriptors, defaults, ranges, and limitations.
- [Simulation](simulation.md) — heat field, procedural water surface, stepping, and reset policy.
- [Audio metadata](audio-metadata.md) — units, events, sanitization, and host mapping.
- [Presets](presets.md) — schema-v1 JSON and the nine example graphs.
- [Integration](integration.md) — source dependency setup and app-specific future integration boundaries.
- [Builds and testing](builds-and-testing.md) — exact commands, shader syntax helper, and recorded evidence.
- [Compatibility](compatibility.md) — SDK/GLES support, budgets, schema compatibility, and unsupported targets.
- [Roadmap](roadmap.md) — P0/P1/P2 implementation and acceptance status.
- [Third-party provenance](third-party.md) — licenses and shader/asset origin.
- [Troubleshooting](troubleshooting.md) — common rendering failures and diagnostics.
- [Preset JSON files](../presets/) — runnable schema examples.
- [Repository instructions](../AGENTS.md), [README](../README.md), [changelog](../CHANGELOG.md), [MIT license](../LICENSE).

The `generator:lightning-spectrum` built-in adds full-output outlined lightning with configurable width
with a procedural colored lightning treatment; see [effects](effects.md),
[API](api.md), and [presets](presets.md).
