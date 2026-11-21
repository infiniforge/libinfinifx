# Third-party provenance and licenses

All built-in fragment shaders in fx-gles/src/main/kotlin/org/infiniforge/fx/gles/FxShaders.kt are original project implementations written for libinfinifx. The sample source fixture is generated procedurally in Kotlin. No game screenshots, ROM assets, console artwork, recordings, shader packs, or third-party effect code are bundled.

Runtime code uses Android GLES/EGL APIs. fx-core has no runtime dependency. Build tooling pins Android Gradle Plugin 8.9.2, Kotlin 2.2.20, Gradle 9.7.1 wrapper, and Kotlin test through Gradle/Maven repositories. The wrapper scripts/JAR and Gradle distribution remain under their upstream Gradle license; they are not relicensed by LICENSE.

Original libinfinifx code is MIT licensed; see ../LICENSE. Add provenance and retain notices before introducing any external code, sample image, font, LUT, or shader.
