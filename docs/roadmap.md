# Roadmap and acceptance state

Status date: 2026-11-20.

| Milestone | Source implementation | Acceptance |
| --- | --- | --- |
| P0 portable contracts | Complete: immutable frame/audio snapshots, typed descriptors, bounded graph models, preset models, graph validation, fixed-step planner, tests. | JVM tests pass on JDK 21 targeting JVM 17. |
| P0 GLES pass-through/resource engine | Implemented and Android-compiled: GLES 3.0 context/thread binding, shader cache, 2D texture copy, graph executor, target pool, state restore, bounded failures, sample Activity. | Local AAR/APK assembly passed. Pass-through pixel correctness, resource reuse/leaks, alpha/UV on a GPU, and sample runtime are not verified on device. |
| P1 composition and display | Implemented in source: alpha/add compositor, CRT/LCD, animated audio-aware rotozoom, kaleidoscope, text scroller, the audio-driven lightning-spectrum generator, runtime parameter overrides, and ten preset graphs. | Current Kotlin/AAR/sample compilation passes. The text-scroller and lightning-spectrum shaders still need source syntax and GLES visual verification. LCD ghosting and CRT phosphor history remain deferred. |
| P1 refraction/simulation/audio | Implemented in source: procedural multiscale water refraction; separate heat field for heat-ripple/fire; optional normalized audio/onset input and per-node event dedup. | Shader visual acceptance, heat stability, synthetic audio modulation and independent state remain pending GLES execution. Water is analytic, not ping-pong wave simulation. |
| P2 polish | Planned: temporal CRT/LCD response, richer mask variants, GPU timing, allocation diagnostics, preset migrations, richer edge/layer options, readback/export contract, additional generators. | Not started. |
| App integrations | An Infinimedia host adapter and temporary random-stage graph policy are implemented in its app checkout. | Build, transparent-surface composition, shader driver compilation, visual correctness, and real-device performance remain unverified. InfiniPlay, InfiniCanvas, and InfiniMesh remain separate future integrations. |

Do not claim “device-ready,” frame rate, hardware accuracy, or shader correctness from source inspection. The smallest next integration task is a device acceptance pass for the Infinimedia artwork pipeline, followed by deliberate effect selection as a separate app change.

The full-width lightning-spectrum generator is source-implemented and integrated
in the current Infinimedia graph. The host parameter editor applies values to
the active pipeline. Portable tests, AAR/sample compilation, and glslang syntax
checks pass; GLES-driver visual appearance and real-device performance remain
unverified.
