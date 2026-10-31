#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
AOSP_ROOT="${AOSP_ROOT:-$(cd .. && pwd)}"
FORMAT_JAVA_BIN="${FORMAT_JAVA_BIN:-$AOSP_ROOT/prebuilts/jdk/jdk21/linux-x86/bin/java}"
JAVA_FORMATTER="$AOSP_ROOT/prebuilts/tools/common/google-java-format/google-java-format-1.27.0-all-deps.jar"
KOTLIN_FORMATTER="$AOSP_ROOT/prebuilts/build-tools/common/framework/ktfmt.jar"
if [ ! -x "$FORMAT_JAVA_BIN" ] || [ ! -f "$JAVA_FORMATTER" ] || [ ! -f "$KOTLIN_FORMATTER" ]; then
    echo 'Set AOSP_ROOT and FORMAT_JAVA_BIN to a workspace with the formatters and Java 21.' >&2
    exit 1
fi
mapfile -t java_files < <(rg --files app/src/main/java native-app/src/main/java tests -g '*.java' | sort)
mapfile -t kotlin_files < <(rg --files app/src/main/java -g '*.kt' | sort)
if [ "${1:-}" = '--check' ]; then
    "$FORMAT_JAVA_BIN" -jar "$JAVA_FORMATTER" --aosp --dry-run --set-exit-if-changed "${java_files[@]}"
    "$FORMAT_JAVA_BIN" -jar "$KOTLIN_FORMATTER" --kotlinlang-style --dry-run --set-exit-if-changed "${kotlin_files[@]}"
else
    "$FORMAT_JAVA_BIN" -jar "$JAVA_FORMATTER" --aosp --replace "${java_files[@]}"
    "$FORMAT_JAVA_BIN" -jar "$KOTLIN_FORMATTER" --kotlinlang-style "${kotlin_files[@]}"
fi
