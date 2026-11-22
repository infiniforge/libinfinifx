# Host integration guidance

No consumer application is imported by this repository. The source dependency is currently unpublished; pin an exact library commit using each host repository's established dependency lock/fetch workflow and use Gradle composite substitution. A Git submodule is suitable only for hosts that already use submodules.

## Standalone source dependency

For a host that uses Git submodules, the library can be added at a declared path:

    git submodule add https://github.com/infiniforge/libinfinifx.git libraries/libinfinifx

For hosts with a source lock and fetch script, declare the repository URL and reviewed commit in that lock, then fetch it into the path used by the composite build. Do not rely on sibling workspace checkouts.

In host settings.gradle.kts:

    includeBuild("libraries/libinfinifx")

In the app module's build.gradle.kts:

    dependencies {
        implementation("io.github.infiniforge.libinfinifx:fx-gles:0.1.0")
    }

The included build substitutes this coordinate with its source project. Pin an explicit reviewed commit; do not depend on a sibling checkout or a Maven artifact claim. fx-gles exposes fx-core transitively. Keep host SDK/AGP settings independent and verify that the composite substitution resolves against the app's Gradle version.

## Infinimedia

Infinimedia consumes the pinned `fx-gles` source dependency in `modules/viewer-audio`. Its app-owned `FxArtworkView` creates and closes `GlesFxEngine` on its GLES 3 render thread, uploads bitmap artwork to a host-owned `GL_TEXTURE_2D`, and draws into its transparent Android surface. Its app-owned `FxEffectPipeline` composes built-in filter IDs, while an FX editor lists available effects and the ordered active chain. The editor can replace, reorder, add, or remove stages (up to twelve); selection persists as playlist items change. These controls use the public library descriptors and graph API and do not change the library contracts.

The host maps the existing smoothed 16 spectrum bands and VU readings from libinfinisound's PCM analysis path into normalized `AudioFrameMetadata` RMS/peak and low/mid/high bands. It does not add a second audio analyzer or synthesize onset/beat events. Rotozoom can respond to energy now; hosts that already have stable beat/onset events may pass their timestamp and unique event IDs. Animated image, video (`GL_TEXTURE_EXTERNAL_OES` in the current playback path), and ANSI presentation remain on their existing paths; the current FX backend accepts only `GL_TEXTURE_2D`. The original artwork view remains a fallback under the effects surface. No player UI, playback logic, or app source is imported by this library.

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

## Lightning spectrum host usage

A host can add an input-free `generator:lightning-spectrum` node, connect it as the `overlay` branch of a `composite:blend`, and connect the image/filter result to `base`. Pass the host's current normalized `AudioFrameMetadata.bands` in each `FxFrameInput`; do not request audio or run analysis from the render thread. By default the node spans the entire output width and height, drawing thin antialiased lightning with no filled bars. Its procedural lightning evolves at 20× real time and six thin side tendrils, each with a secondary twig, are drawn per trace. Hosts can override `animationSpeed` and integer `branchCount` (1–8); line coverage follows output-pixel derivatives, with a modestly wider core; the seeded spine stays inside each band without clamping at the edge. Present zero-valued bands have zero height; missing bands may use the idle baseline. To color the lightning from a host-detected artwork palette, provide `FxMetadataChannels` with `fx.palette.count` (1–8) and `fx.palette.color.<index>.r/g/b` sRGB channels (each 0–1). Each band uses a palette entry in order. Missing palette metadata falls back to cyan. The library does not extract palettes from images. Rotozoom and kaleidoscope rotate autonomously and can use host-supplied BPM, energy, and uniquely identified onset events. Graph parameters may be updated from an app-owned editor; UI and saved-setting policy remain host-owned.
