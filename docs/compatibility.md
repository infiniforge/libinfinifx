# Compatibility and limits

## Platform

- Android runtime minimum: API 26.
- Primary backend: OpenGL ES 3.0 or newer with a current context on the calling thread.
- Android library compile SDK: 36.
- Build pins: Gradle 9.7.1, AGP 8.9.2, Kotlin 2.2.20, JVM bytecode target 17.
- GLES 2.0 contexts cannot execute this backend. No GLES2, Vulkan, AGSL/RuntimeShader, or desktop backend is implemented.
- No API requires Android 13. Android Context/Activity is not retained by fx-core.

The engine queries GLES/GLSL versions and maximum texture size. It uses RGBA8, so float color/render extensions are not required. A device with GLES3 but insufficient texture size or memory receives a render failure. Default pool budget is 128 MiB for internal image/simulation targets; increase only with device memory testing. Programs are compiled lazily and cached per context.

EXTERNAL_OES is explicitly unsupported by built-ins. Copy into a regular 2D texture on the host GL thread. No implicit conversion or extension assumption is made.

## API and package versioning

Gradle package version is 0.1.0. Effect IDs are namespaced and each descriptor has a version. Graph/preset JSON schema version is 1. Unknown major schema versions fail decode; unknown effect/parameter IDs fail graph validation. No automatic migration API exists yet. Public model changes before 1.0 may be breaking and must update docs and tests.

Coordinates use normalized GL bottom-left space. Source orientation, content crop, sampling and destination viewport are explicit. Pixel aspect ratio is passed to the host; app-specific fit, emulator screen layout, and input mapping remain outside the engine.

## Known effect constraints

- Heat simulation and fire use 8-bit field channels; this is a bounded artistic model, not physical heat transfer.
- Water is a procedural multiscale height field. It is not a full shallow-water solver.
- CRT/LCD are stylized image effects; no temporal phosphor or LCD response history yet.
- Game Boy palette mode quantizes RGB luminance to four colors. Original palette/index data is not reconstructed.
- No pass timing query, GPU benchmark, screenshot golden, or device visual test is recorded.
- Source artifacts are not published to a Maven repository. Pin a repository commit in the host.

## Non-goals

Audio decoding/playback, emulator control and layout, image codecs, 3D parsing, editor timeline/export, user shaders/scripts, global simulation fields, and app settings UI remain owned by consumer applications or other libraries.
