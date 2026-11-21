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
