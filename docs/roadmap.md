# Roadmap and acceptance state

Status date: 2026-11-20.

| Milestone | Source implementation | Acceptance |
| --- | --- | --- |
| P0 portable contracts | Complete: immutable frame/audio snapshots, typed descriptors, bounded graph models, preset models, graph validation, fixed-step planner, tests. | JVM tests pass on JDK 21 targeting JVM 17. |
| P0 GLES pass-through/resource engine | Implemented and Android-compiled: GLES 3.0 context/thread binding, shader cache, 2D texture copy, graph executor, target pool, state restore, bounded failures, sample Activity. | Local AAR/APK assembly passed. Pass-through pixel correctness, resource reuse/leaks, alpha/UV on a GPU, and sample runtime are not verified on device. |
| P1 composition and display | Implemented in source: alpha/add compositor, CRT and LCD shader effects, runtime parameter overrides and six preset graphs. | glslang syntax check passed; GLES driver shader compilation, visual semantics at multiple sizes, alpha, screenshots and device stability pending. LCD ghosting and CRT phosphor history remain deferred. |
| P1 refraction/simulation/audio | Implemented in source: procedural multiscale water refraction; separate heat field for heat-ripple/fire; optional normalized audio/onset input and per-node event dedup. | Shader visual acceptance, heat stability, synthetic audio modulation and independent state remain pending GLES execution. Water is analytic, not ping-pong wave simulation. |
| P2 polish | Planned: temporal CRT/LCD response, richer mask variants, GPU timing, allocation diagnostics, preset migrations, richer edge/layer options, readback/export contract, additional generators. | Not started. |
| App integrations | Separate milestones only. | No Infinimedia, InfiniPlay, InfiniCanvas or InfiniMesh source was changed. |

Do not claim “device-ready,” frame rate, hardware accuracy, or shader correctness from Kotlin compilation. The smallest next integration is a separate Infinimedia host-side adapter that maps its existing analyzer snapshot into AudioFrameMetadata and renders one chosen graph on a verified GLES3 surface, without replacing its existing visualizer until that adapter is independently reviewed.
