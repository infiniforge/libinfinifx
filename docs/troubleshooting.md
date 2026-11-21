# Troubleshooting

## Black frame

Confirm the engine was created after a GLES 3 context became current and every call is on the same GL thread/context. Check that source texture name is nonzero, target dimensions match FxGeometry.destination dimensions, source dimensions match FxGeometry.source dimensions, and output FBO is complete. Inspect FxRenderResult.Failure and GlesFxDiagnostics. EXTERNAL_OES is unsupported; copy to GL_TEXTURE_2D first.

## Shader compile/link failure

The Failure message includes the driver shader or program log. Capture GL/GLSL version and device. Built-ins use GLSL ES 3.00 and require GLES 3.0. Do not assume an ES2 context can run them. Report a reproducible shader log with the graph and GPU/driver.

## Upside-down, rotated, or cropped image

Input texture UV origin is bottom-left. Set FxGeometry.contentRect and displayOrientationDegrees from host-owned source metadata. The frame geometry overrides the primary FxTexture UV metadata. Check that crop rect uses normalized lower-left GL coordinates. The sample's red/green upper and blue/yellow lower quadrants plus opposite-corner markers expose mirroring.

## Wrong pixel scaling or moiré

Choose FxSampling.NEAREST for pixel art. CRT scanline spacing is based on the logical oriented source raster; phosphor mask pitch is output pixels. InfiniPlay still owns source resolution and final display rectangles. The engine uses the destination displayRect but does not guess emulator layouts.

## Dark fringes or incorrect transparency

Declare source alpha as OPAQUE, STRAIGHT, or PREMULTIPLIED correctly. Built-in transforms convert through linear premultiplied pixels; blend is computed in linear sRGB. Ensure the target's alphaMode/colorEncoding declaration matches how the host later samples it. For transparent fire, keep a distinct overlay target and alpha-over it; do not sample and write the same texture.

## Unexpected colors or gamma

Color encoding defaults to sRGB. UNKNOWN is assumed sRGB when conversion is needed. Built-ins convert to linear sRGB while processing and convert to target metadata on output. A bypass with matching declarations copies values directly. Avoid applying a second sRGB transfer in the host.

## External texture unsupported

GL_TEXTURE_EXTERNAL_OES is not a GL_TEXTURE_2D. The engine returns unsupported-texture-target. Add an explicit host-side copy pass on the correct GL thread; the library does not assume EGL extension support.

## Context loss or background/resume

The engine is bound to the EGL context that was current during construction. Close before ordinary surface destruction while that context is current. After context loss, discard the engine and create another on the new GL thread. Do not reuse old texture/program names.

## Performance or resource limit

Use FxGlesDiagnostics to inspect allocated RGBA8 bytes and cached program count. Reduce output or simulation resolution, reduce active graph nodes, and release unused graphs. The default pool is capped at 128 MiB. No GPU timer or FPS guarantee is implemented; measure on the target device. Avoid creating the engine per frame or rendering on the audio thread.
