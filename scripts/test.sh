#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
AOSP_ROOT="${AOSP_ROOT:-$(cd .. && pwd)}"
ANDROID_JAR="$AOSP_ROOT/prebuilts/sdk/36/public/android.jar"
JSON_JAR="${JSON_JAR:-$(find "$HOME/.gradle/caches" -name json-20180813.jar -print -quit)}"
if [ -z "$JSON_JAR" ]; then echo 'Set JSON_JAR to a JVM org.json jar'; exit 1; fi
mkdir -p build/tests
javac --release 17 -encoding UTF-8 -cp "$ANDROID_JAR:$JSON_JAR" -d build/tests app/src/main/java/com/autodroid/Store.java tests/RuleMatchTest.java
java -cp "build/tests:$JSON_JAR:$ANDROID_JAR" com.autodroid.RuleMatchTest
