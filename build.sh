#!/bin/sh
set -eu
./gradlew :fx-core:test "$@"
./gradlew :fx-gles:testDebugUnitTest :fx-gles:assembleDebug :sample:assembleDebug "$@"
