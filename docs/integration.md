# Host integration guidance

No consumer application is modified or imported by this repository. The source dependency is currently unpublished; pin a Git submodule commit in each host repository and use Gradle composite substitution.

## Standalone source dependency

From an app repository, add the library at a declared path and commit the gitlink:

    git submodule add https://github.com/infiniforge/libinfinifx.git libraries/libinfinifx

In host settings.gradle.kts:

    includeBuild("libraries/libinfinifx")

In the app module's build.gradle.kts:

    dependencies {
        implementation("io.github.infiniforge.libinfinifx:fx-gles:0.1.0")
    }

The included build substitutes this coordinate with its source project. Pin the submodule to an explicit reviewed commit; do not depend on a sibling checkout or a Maven artifact claim. fx-gles exposes fx-core transitively. Keep host SDK/AGP settings independent and verify that the composite substitution resolves against the app's Gradle version.

## Infinimedia

InfiniMedia owns playback, the visual pane, Android surface lifecycle, and its existing pure Kotlin visualization-engine. It also owns the bridge from libinfinisound's existing PCM/playback analysis to the optional FxFrameInput.audio snapshot. Do not duplicate audio analysis here or refactor the existing visualizer as part of adding this dependency.

A future integration can acquire/copy a GL_TEXTURE_2D on the visual rendering thread, use the player presentation clock and 16 existing spectrum bands, build the app-owned adapter described in [audio metadata](audio-metadata.md), and render to a distinct target before display. Preserve video/image paths when audio is absent. No Infinimedia EGL surface or texture adapter exists in this library yet.

## InfiniPlay

InfiniPlay owns Libretro cadence, source resolution, aspect handling, screen composition, and touch mapping. Its final screen split is app-owned; the libinfinifx output should be placed where InfiniPlay chooses. Never move/scale DS/3DS coordinates in FX.

This library requires GLES 3.0. The current InfiniPlay tree includes GLES2 calls in play_screens.h, so verify the active Libretro EGL version before proposing an integration. A GLES2 context cannot run these shaders. A later integration must choose an explicit ES3-capable final pass or another adapter and test source frame delivery, DS/3DS rectangles, orientation, and touch-to-source mapping. This repository does not replace GLRetroView or add a hidden second renderer.

## InfiniCanvas

InfiniCanvas owns image decoding, edits, document state, authoring, undo/history, and export. A preview adapter can upload an owned preview frame into a 2D texture and render an FX graph into a separate preview target. Do not change source documents or file URIs from this library. Export/readback and reapplication of non-destructive effect parameters are not provided yet; keep a separate effect recipe in app-owned document state until a library render/export contract exists.

## InfiniMesh

InfiniMesh owns 3D parsing, model state, camera, and scene rendering. Current libinfinemesh mesh-android is GLES2-based and texture/material support is planned. A later post-process hook would need a GLES3-compatible scene output texture or explicit copy/interop stage; this library does not parse geometry or change mesh coordinates. Preserve the model viewer's context lifecycle and depth/viewport state.

## Lifecycle checklist for every host

- Create GlesFxEngine on the rendering thread after the required context is current.
- Retain the source texture and its context until render completes.
- Use a separate caller framebuffer/texture target; do not sample a texture while writing into it.
- Pass monotonic render timestamps/delta, source dimensions/orientation/crop, destination viewport, and optional normalized audio metadata.
- Call resize/resetHistory at resize, seek, track change, or pause/resume boundaries as appropriate.
- Close while the creating context is current. Recreate the engine after EGL context loss.
- Handle FxRenderResult.Failure by retaining a valid host frame or bypassing FX; do not block audio or emulation threads.
- Keep display placement and input mapping in the app.
