# Rendering backend

## GLES and resources

The backend requires a current OpenGL ES 3.0+ context (Android API 26+). GlesCapabilities.query reads the actual GL/GLSL version, extension set, and GL_MAX_TEXTURE_SIZE. Built-ins use RGBA8 textures/FBOs, fullscreen triangle shaders, and no float-render-target extension. No CPU bitmap allocation/readback occurs in the live pass loop.

The host owns its EGL context, source texture, output framebuffer, and any Surface/SurfaceTexture. The engine owns cached shader programs, pooled intermediate RGBA8 textures/FBOs, and per-node heat field targets until close or context loss. Source textures must use texture name greater than zero and remain alive during render. Output FBO name zero is valid. The attached texture name is optional but recommended for alias detection.

Each pass binds an internal framebuffer or caller target, sets a viewport, uses cached shader programs, and draws three vertices via gl_VertexID. The engine wraps render, resize, and history-reset operations in a state guard that restores separate draw/read framebuffer bindings, viewport, scissor box, current program, active texture and unit-0/unit-1 2D bindings, blend/depth/cull/scissor/dither enables, color write mask, and clear color. Disabled generator clears are clipped to the declared target viewport. Source min/mag filters are changed temporarily to match FxTexture.sampling, then restored. Vertex buffers and VAOs are not used. A pre-existing GL error may be reported at the end of a draw because GLES error flags are context-global.

The pass pool reuses matching dimensions, releases textures after the final graph consumer, trims free targets on resize, and enforces a default 128 MiB budget across temporary and retained heat surfaces. Requested dimensions are checked against the queried maximum. Budget exhaustion, incomplete framebuffer, and shader compile/link failures return a render failure with an actionable message. GlesFxDiagnostics reports allocated RGBA8 texture bytes, shader-program count, GL/GLSL versions, and max texture size. GPU timestamps and per-pass timing are not implemented.

## Texture targets, UVs, and geometry

Inputs must be GL_TEXTURE_2D. EXTERNAL_OES is not treated as equivalent: rendering it returns unsupported-texture-target. A host may copy the external image into a regular 2D texture on its own GL thread before calling libinfinifx; that adapter is not included.

Texture UV origin is OpenGL bottom-left. FxGeometry.contentRect crops normalized source coordinates, displayOrientationDegrees rotates 0/90/180/270, and displayRect maps output into a normalized lower-left target viewport. FxRenderTarget.viewport is composed with displayRect. The app chooses displayRect using its aspect/layout rules; the engine does not reposition DS/3DS screens or rewrite touch coordinates. Pixel aspect ratio is exposed to the host but fit/letterbox calculation remains host-owned.

FxTexture.sampling selects nearest or linear input sampling for each draw. The backend temporarily changes sampler min/mag parameters on the shared texture object and restores both values before return. Internal output textures are clamped to edge. That makes nearest sampling usable for pixel art without permanently mutating host texture configuration.

## Alpha and color

FxTexture declares OPAQUE, STRAIGHT, or PREMULTIPLIED alpha plus LINEAR, SRGB, or UNKNOWN encoding. Unknown encoding is treated as sRGB when conversion is needed. The source bypass preserves sampled values when source and target declarations match; when they differ it converts through linear premultiplied RGBA.

Built-in image effects convert input pixels to linear premultiplied RGBA before processing and encode to the output target's declared alpha/color mode. The compositor decodes both layers to linear sRGB, applies alpha-over or additive blending, then converts to the output target mode. Internal RGBA8 surfaces store logically sRGB-encoded premultiplied color. Simulation textures use RGBA channels for field values, not image color.

The backend disables blending for its fullscreen shader passes and writes each graph node to a distinct target. It does not clear user targets except a disabled generator output; the host controls background/letterbox pixels outside the requested viewport. For no-effect exact representation preservation, use the same alpha/color declarations on source and target.

## Resize, pause, and context loss

A source/output resize resets private simulation history. Pausing can call resetHistory(PAUSED); heat buffers clear, while water's elapsed autonomous time is retained. Resume may reset other temporal state. On EGL context loss, do not reuse the engine. Drop it and create a new one from the new current context; the old context owns and frees its dead names. close normally requires the original context current.

Surface lifecycle remains app-owned. Do not call rendering from an audio callback, MediaCodec callback, or UI thread unless that is the designated GL thread. Export/readback, synchronization fences across contexts, external OES import, and shared-context resource ownership are future work.


## Graph source geometry

For effect graphs, the executor copies the input once into a pooled RGBA8 target at destination dimensions, clearing the surrounding area and placing the source inside `FxGeometry.displayRect` after applying its crop and orientation. Sequential effects and branches therefore share a stable destination-sized canvas without stretching a non-matching source aspect ratio at intermediate passes. A source-only pass-through bypass skips this normalization and draws directly into the requested display rectangle. The source dimensions in frame metadata remain the original logical raster for CRT/LCD sampling.

The lightning-spectrum generator uses a single cached GLES fragment program and no input textures or history surfaces. Its sixteen bands are supplied as four vec4 uniforms; with defaults, widthScale and maxHeight are 1.0, so the render area reaches all four output edges. The shader draws lightning paths without a filled rectangular bar body. `animationSpeed` multiplies shader time without changing audio band heights; its 20× default makes the spine, branches, and flicker evolve quickly. `fwidth` derives the antialiasing transition for the spine, tendrils, and twigs from screen-space derivatives. The bright core is modestly wider than the previous hairline (about 1.4 output pixels at nominal scale), with screen-space antialiasing; branch count, rather than line width, creates the fuller silhouette. Spine jitter stays inside a margin without clamping, avoiding flat vertical segments at a clipped edge. Six side tendrils per trace are drawn by default and `branchCount` is bounded to 1–8. Up to eight sRGB palette entries arrive through bounded metadata channels and color the lightning; the shader converts them to linear light before premultiplied composition. The output is transparent premultiplied RGBA and can be composited by the normal graph blend node.


## Shader source normalization

Built-in shader stages are stored as indented Kotlin raw strings. The GLES backend applies the same `trimIndent()` normalization before compiling vertex and fragment source, so the GLSL ES `#version 300 es` directive is the first line passed to the driver. Shared color helpers are inserted only after the precision declaration. `scripts/validate-shaders.py` normalizes extracted strings the same way before running glslang; that syntax check is separate from device-driver compilation.
