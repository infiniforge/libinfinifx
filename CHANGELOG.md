# Changelog

## 0.1.1 — Unreleased

- Add full-width `generator:lightning-spectrum` with an original animated lightning shader, normalized 16-band input, an idle preview, and an alpha-compositable preset.

- Add registry-backed `BuiltinFx.availableFilters` and `availableGenerators` lists.
- Add host-supplied `FxTextInput` and `generator:text-scroller` with configurable font family, angle, size, scroll speed, and timed title overlays.
- Add the text-scroller preset and document host data selection versus library processing ownership.
- Add animated `distort:rotozoom` with optional normalized RMS/low-band and onset-event response.
- Add configurable `distort:kaleidoscope`.
- Add `album-art-rotozoom` and `kaleidoscope` preset graphs.
- Preserve source aspect/crop/orientation across multi-pass graph stages with one destination-sized normalization pass.
- Normalize graph source geometry once so multi-pass chains preserve source aspect ratio.
- Document the Infinimedia host adapter and its temporary random-stage policy.


## 0.1.0 — 2026-11-20

- Added portable effect descriptors, frame/audio metadata, graph validation, fixed-step planning, and built-in preset models.
- Added GLES 3.0 graph executor, RGBA8 target pool, shader cache, state restoration, and original CRT/LCD/water/heat/fire/composition shaders.
- Added schema-v1 JSON preset codec, six preset examples, and a standalone Android sample.
- Added a repeatable glslangValidator helper for built-in shader source syntax checks.
- Six portable JVM tests and Android preset-codec tests pass; Android AAR/sample build passes with SDK 36 configured; all nine shader stages pass glslang syntax validation.
- Real-device shader/runtime, visual correctness, and performance acceptance remain unverified.
