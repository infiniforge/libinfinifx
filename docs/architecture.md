# Architecture

libinfinifx is a rendering library, not an Android application. It consumes a caller-owned GL_TEXTURE_2D plus an immutable frame snapshot and graph, then draws into a caller-owned framebuffer. It never opens files, decodes images/audio, starts playback, or owns an Activity, Surface, EGL context, or display layout.

    Host app: source texture + clock + optional normalized metadata + graph
                                  |
                        fx-core public API
                                  |
                  graph validation and topological plan
                                  |
                  fx-gles GLES3 pass executor
                    |             |             |
                filters       generators     compositor
                  ____________ reusable target pool ______/
                                  |
                       caller framebuffer / target

## Modules

| Module | Platform | Ownership |
| --- | --- | --- |
| fx-core | JVM, Android-free | Frame/audio metadata, typed parameter descriptors, effect contracts, graph and preset models, graph validation, fixed-step planning, built-in descriptors/preset definitions. |
| fx-gles | Android API 26+, GLES 3.0 | EGL-thread-bound engine, shader compilation/cache, RGBA8 framebuffer/texture pool, built-in effect runtimes, private heat state, bounded schema-v1 JSON codec. |
| sample | Android API 26+, GLES 3.0 | Standalone demonstration Activity, generated asymmetric source fixture, preset selector, synthetic metadata. |

There are no app or sibling-project imports and no runtime library dependencies beyond Android/GLES and fx-core. Android SDK and Gradle plugins are build-time dependencies. No Maven artifact is published.

## Graph model and execution

The graph is a DAG of at most 64 nodes and 128 connections. The synthetic node ID source denotes the caller's input texture. Each effect has one color output and named required input ports. A connection maps one source output into one input port; blend currently has base and overlay ports. Nodes are topologically sorted in stable input order. Unknown effects/parameters, missing or duplicate ports, cycles, missing output, dangling nodes, and output nodes that feed later nodes fail validation before GPU work.

Generators allocate an output without input. Transformers read image and write a distinct output. The compositor reads distinct base/overlay textures and writes an explicit blend result. No pass samples from the texture attached to its output. Intermediate resources are released to the pool after their final consumer. The output node writes directly to the host target. A source-only graph uses one copy shader and no pooled target.

Node runtimes are keyed by graph node ID and effect ID. Heat-ripple and fire instantiate separate simulation textures per node. They do not share a singleton or read each other's fields. The graph is the only mechanism for routing pixels between effects.

## Lifecycle and compatibility choices

GLES 3.0 is the first backend because the library needs fullscreen-triangle shaders, integer texture formats, and reliable FBO support. The engine captures the creating EGL context and thread. Calls must run there. Context loss requires a new engine; no app context is created or retained on behalf of a consumer.

Targets and simulation fields use RGBA8. This avoids a float renderability dependency, at the cost of clipping and 8-bit field precision. A bounded 128 MiB default pool holds reusable per-frame surfaces and retained history fields. The pool checks the queried maximum texture size and reports a resource failure rather than growing without limit.

Frame geometry carries source crop/orientation and destination placement. The engine maps source UVs from bottom-left normalized GL coordinates and applies the destination display rectangle inside the requested framebuffer viewport. The host remains responsible for selecting the display rectangle from pixel aspect ratio, emulator screen layout, and its own letterbox policy.

## Deliberate omissions

There is no general visual-programming editor, user GLSL, shader hot reload, shared mutable simulation, temporal CRT phosphor or LCD ghost history, arbitrary layer stacks beyond explicit graph nodes, GPU timestamp query API, export/readback pipeline, or GLES2/Vulkan/AGSL backend. An Infinimedia artwork/static-image host adapter now consumes the public API; other application integrations remain separate and require runtime acceptance.

## Lightning spectrum generator

The input-free `generator:lightning-spectrum` reads only the current immutable audio-band snapshot, timestamp, and graph parameters. It writes a centered 90%-width transparent layer by default with sixteen colored jagged electric columns. A host graph connects this node to an explicit blend; the effect has no dependency on album-art or audio-decoder code and allocates no per-frame textures.
