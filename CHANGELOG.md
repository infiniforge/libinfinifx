# Changelog

## 0.1.1 — Unreleased

- Normalize built-in GLES shader strings before compilation so the #version directive is on the first line.
- Add animated `distort:rotozoom` with optional normalized RMS/low-band and onset-event response.
- Update `distort:rotozoom` to descriptor v3 with tempo-smoothed, bounded rotation and smooth RMS/onset modulation to prevent angle jumps.
- Add configurable `distort:kaleidoscope` and the `album-art-rotozoom` and `kaleidoscope` preset graphs.
- Preserve source aspect/crop/orientation across multi-pass graph stages with one destination-sized normalization pass.
- Render `generator:lightning-spectrum` edge to edge with outline-only lightning, linear normalized band gain, and optional eight-color palette metadata channels. Present zero-valued bands still produce zero-height output.
- Document the Infinimedia host adapter and its temporary random-stage policy.


## 0.1.0 — 2026-11-20

- Added portable effect descriptors, frame/audio metadata, graph validation, fixed-step planning, and built-in preset models.
- Added GLES 3.0 graph executor, RGBA8 target pool, shader cache, state restoration, and original CRT/LCD/water/heat/fire/composition shaders.
- Added schema-v1 JSON preset codec, six preset examples, and a standalone Android sample.
- Added a repeatable glslangValidator helper for built-in shader source syntax checks.
- Six portable JVM tests and Android preset-codec tests pass; Android AAR/sample build passes with SDK 36 configured; all nine shader stages pass glslang syntax validation.
- Real-device shader/runtime, visual correctness, and performance acceptance remain unverified.
