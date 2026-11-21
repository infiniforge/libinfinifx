# Builds and testing

## Toolchain and commands

Pinned build tools: Gradle 9.7.1 wrapper (SHA-256 checked), Kotlin 2.2.20, Android Gradle Plugin 8.9.2, compile SDK 36, min SDK 26. JVM bytecode target is 17. Android modules need platform 36/build tools 35.0.0. No sibling checkout or source dependency is needed.

Portable headless tests:

    ./gradlew :fx-core:test --max-workers=1 --console=plain

Optional GLSL source syntax check (requires `glslangValidator` on PATH):

    python3 scripts/validate-shaders.py

Android library plus sample:

    ./gradlew :fx-gles:assembleDebug :sample:assembleDebug --max-workers=1 --console=plain

Combined helper:

    ./build.sh --max-workers=1 --console=plain

Termux command verified in this checkout (SDK at `$HOME/android-sdk`; the option is passed because the already-running Gradle daemon did not inherit the exported SDK path):

    ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain

For Termux with its installed aapt2, if needed, add `-Pandroid.aapt2FromMavenOverride="$(command -v aapt2)"` to the Gradle invocation.

## Recorded results

Environment: Termux on Android, Linux aarch64, OpenJDK 21.0.13.1, Gradle 9.7.1 wrapper, date 2026-11-20. Android SDK 36 and build tools 35.0.0 were installed under `$HOME/android-sdk`; ANDROID_HOME was initially unset.

- PASS: `./gradlew :fx-core:test --max-workers=1 --console=plain` — 6 portable JVM tests passed, targeting JVM 17.
- PASS: `ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain` — JVM tests, `:fx-gles:assembleDebug`, and `:sample:assembleDebug` completed. The AAR and debug APK were produced.
- PASS: `python3 scripts/validate-shaders.py` with glslangValidator on PATH; the vertex shader plus all eight fragment shader variants passed (9 shader stages). This is a source syntax check, not a GLES device-driver compile/link test.
- Android build emitted an SDK XML version 4 compatibility warning from the installed SDK tools, an experimental Termux aapt2 override warning, and Gradle deprecation notices. The build completed successfully despite them.
- The first Android compile exposed mismatched Java/Kotlin targets and a Kotlin cross-module smart-cast error. Both were fixed; the final combined command passed.
- `adb devices -l` reported no connected device. No shader-driver compilation, screenshot comparison, surface/context recreation, GPU timing, memory stress, FPS, thermal, or sample runtime test was run. Visual correctness and performance are **not verified on device**.
- PASS: GitHub Actions run [44845197052](https://github.com/infiniforge/libinfinifx/actions/runs/44845197052) for commit `c092d64` completed successfully on an SDK-equipped hosted runner. It ran the combined test/AAR/APK build; it does not perform GPU visual acceptance. The runner added non-failing maintenance annotations for pinned Actions v4/Node runtime versions.

## Unreleased effect and geometry update (2026-11-20)

- PASS: `./gradlew :fx-core:test --max-workers=1 --console=plain` — 7 portable tests passed, including the new rotozoom/kaleidoscope registry and preset validation.
- PASS: `python3 scripts/validate-shaders.py` — 11 GLES shader stages passed glslang syntax validation, including rotozoom and kaleidoscope.
- PASS: `ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain` — portable tests, fx-gles Android unit-test task, AAR assembly, and sample APK assembly completed.
- A first `./build.sh` without Android SDK environment failed during `:fx-gles:testDebugUnitTest` dependency setup because SDK location was not set. The documented retry with `ANDROID_HOME` passed.
- The Infinimedia adapter and its host-side unit tests have not yet been built in this checkout. No GLES driver, Android device, screenshot, or performance run was performed. The source changes are not visually verified on device.

## Portable coverage

FxCoreTest covers parameter ranges/defaults, branch/topological ordering, missing required inputs, cycle rejection, event ID dedup/reset, invalid metadata sanitization, and bounded fixed-step planning. FxPresetJsonTest covers all built-in preset round-trips, graph validation after decode, unsupported schema versions, and unknown-field rejection. These are not GPU shader tests.

## Android/device acceptance still required

On a GLES 3 emulator/device, build the sample and test:
1. Four distinct source quadrants and opposite-corner markers retain expected orientation and crop.
2. Source-only pass-through at equal size preserves opaque and transparent values; test both nearest and linear upscaling.
3. CRT and LCD look distinct at several source/output sizes and rotation values.
4. Water visibly refracts a checker/recognizable image, remains animated with audio off, and differs between calm/rough parameters.
5. Heat-ripple and fire evolve with audio null, stay finite after varied frame intervals, and two nodes remain independent.
6. Alpha/add composition uses separate branch targets and produces expected transparent edges.
7. Resize, seek, pause/resume, repeated start/stop, target budget exhaustion, and context recreation return valid frames/errors without stale output.
8. Record device model, Android, GLES/GLSL versions, output size, graph, observed frame times/FPS, and thermal conditions. No performance target is claimed until measured.

The configured CI workflow compiles JVM tests and Android modules on a hosted Android SDK runner. CI cannot establish GPU visual/device acceptance.

## Lightning-spectrum generator addition (2026-11-20)

- PASS: `./gradlew :fx-core:test --max-workers=1 --console=plain` — 8 portable JVM tests passed.
- PASS: `ANDROID_HOME=/data/data/com.termux/files/home/android-sdk ./build.sh --no-daemon --max-workers=1 --console=plain` — 3 GLES preset-codec tests passed; the `fx-gles` AAR and sample APK assembled.
- PASS: `python3 scripts/validate-shaders.py` — all 12 stages passed glslang syntax checks, including `LIGHTNING_SPECTRUM`.
- `adb devices -l` reports no connected device. GLES driver compilation, actual lightning appearance, surface composition, and device performance remain unverified.


## Shader directive fix (2026-11-21)

The Infinimedia diagnostic log showed GLES rejecting every graph at shader compile time because Kotlin raw strings had a leading blank line before `#version`. The backend now normalizes vertex and fragment source before helper injection and compilation.

- Added `FxShaderSourceTest` to check first-line normalization for all built-in shader stages and the lightning shader.
- Updated `python3 scripts/validate-shaders.py` to extract the actual raw-string bodies and normalize them before syntax validation, matching the backend behavior.
- PASS: python3 scripts/validate-shaders.py — all 12 shader stages pass glslang syntax validation after source normalization.
- PASS: ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain — fx-gles unit tests (5 total, 0 failures), AAR assembly, and sample APK assembly completed. fx-core:test was up to date.
- PASS: Infinimedia ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain completed against this local library checkout; the host app APK assembled.
- adb devices -l reports no connected device. GLES driver compilation, actual rotozoom/crop display, and vertical placement remain not verified on device. The supplied screenshot predates this fix and its accompanying log records the shader compile error.


## Centered spectrum and bounded rotozoom (2026-11-21)

- PASS: `ANDROID_HOME="$HOME/android-sdk" ./gradlew :fx-core:test :fx-gles:test --no-daemon --max-workers=1 --console=plain` — 25 test executions (10 fx-core, 8 debug GLES, 7 release GLES), no failures or skips.
- PASS: `python3 scripts/validate-shaders.py` — all 12 GLES shader stages pass glslang syntax validation, including the changed lightning-spectrum and rotozoom shaders.
- PASS: `ANDROID_HOME="$HOME/android-sdk" ./build.sh --no-daemon --max-workers=1 --console=plain` — Android library AAR and sample APK assembled successfully.
- The centered spectrum defaults to 90% width (5% margins); the GLES shader-source test asserts that zero-height spectrum columns take the transparent output path. Rotozoom motion tests cover smoothed speed changes, continuous integrated phase, the ±4 rad/s cap, and bounded frame delta.
- An initial Gradle invocation without `ANDROID_HOME` failed with “SDK location not found”; rerunning with the documented SDK path passed.
- `adb devices -l` reports no connected device. GPU-driver shader compilation, visual bar geometry, runtime rotozoom smoothness, and performance are not verified on device.
