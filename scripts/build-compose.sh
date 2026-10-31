#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f build/offline/debug.keystore ]; then
    echo 'Missing existing signing key: build/offline/debug.keystore' >&2
    echo 'Restore the original key before building an update.' >&2
    exit 1
fi
BUILD_TYPE="${BUILD_TYPE:-release}"
case "$BUILD_TYPE" in
    release) task='assembleRelease' ;;
    debug) task='assembleDebug' ;;
    *) echo 'BUILD_TYPE must be release or debug.' >&2; exit 1 ;;
esac
if [ -z "${GRADLE_BIN:-}" ]; then
    if command -v gradle >/dev/null 2>&1; then
        GRADLE_BIN="$(command -v gradle)"
    else
        for candidate in "$HOME"/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle; do
            if [ -x "$candidate" ]; then
                GRADLE_BIN="$candidate"
                break
            fi
        done
    fi
fi
if [ -z "${GRADLE_BIN:-}" ]; then
    echo 'Install Gradle 8.13 or set GRADLE_BIN.' >&2
    exit 1
fi
"$GRADLE_BIN" ":app:$task" --console=plain "$@"
mkdir -p build/compose
cp "app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE.apk" build/compose/AutoDroid-compose.apk
AOSP_ROOT="${AOSP_ROOT:-$(cd .. && pwd)}"
if [ -f "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" ]; then
    java -jar "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" verify --verbose build/compose/AutoDroid-compose.apk
fi
echo "APK: $PWD/build/compose/AutoDroid-compose.apk"
