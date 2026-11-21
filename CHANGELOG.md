# Changelog

## 0.1.0 — 2026-11-20

- Added portable effect descriptors, frame/audio metadata, graph validation, fixed-step planning, and built-in preset models.
- Added GLES 3.0 graph executor, RGBA8 target pool, shader cache, state restoration, and original CRT/LCD/water/heat/fire/composition shaders.
- Added schema-v1 JSON preset codec, six preset examples, and a standalone Android sample.
- Added a repeatable glslangValidator helper for built-in shader source syntax checks.
- Six portable JVM tests and Android preset-codec tests pass; Android AAR/sample build passes with SDK 36 configured; all nine shader stages pass glslang syntax validation.
- Real-device shader/runtime, visual correctness, and performance acceptance remain unverified.
