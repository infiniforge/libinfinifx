# libinfinifx development guidance

This repository owns reusable Android GPU effects and compositing only. It does not own app source, player clocks, audio analysis, file formats, Android UI, EGL contexts, or another effect's private simulation state.

## First-read order

1. README.md
2. docs/architecture.md and docs/api.md
3. docs/rendering.md and docs/effects.md
4. The relevant simulation, audio, preset, compatibility, and integration docs
5. docs/builds-and-testing.md before claiming verification

## Ownership rules

- Keep fx-core portable and free of Android/application imports.
- Keep fx-gles independent of consumer apps. Apps supply GL textures, clocks, normalized metadata, output targets, lifecycle, and layout.
- Create, render, update, resize, reset, and release GlesFxEngine on its creating thread with its EGL context current.
- Keep each node's history private. Effects must not call or mutate other effects.
- Add reusable behavior through the public library API; do not duplicate audio decoding/analysis, image codecs, 3D model parsing, emulator geometry or UI settings frameworks.
- Do not read or write another app's private data. Do not require InfiniFolder or another app.
- Do not introduce user GLSL, unbounded graph execution, unbounded textures, or implicit GPU feedback loops.
- Preserve source texture ownership and document target ownership, alpha/color assumptions, and failure behavior.
- Use only original shaders/assets or record provenance and licenses in docs/third-party.md.

## Change discipline

- Update docs/effects.md, docs/api.md, docs/rendering.md, docs/simulation.md, or docs/presets.md when code changes those contracts.
- Add portable tests for API/graph/time behavior and run requested build/test commands. Device-only behavior must be labeled unverified until actually run.
- Keep app integrations in separate app milestones; integration.md is guidance only.
- Version graph/preset schema independently from Gradle package version.
- Do not edit generated docs/ECOSYSTEM.md copies manually. The authoritative component manifest and generated copy synchronization live in the separate infiniforge/.github repository. Propose or review manifest updates there through its generator and PR workflow.

## Build

Portable tests: ./gradlew :fx-core:test --max-workers=1 --console=plain

Android modules and sample: ./build.sh --max-workers=1 --console=plain
