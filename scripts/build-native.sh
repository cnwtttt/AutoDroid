#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$#" -ne 0 ]; then
    echo 'The native AOSP build is offline and takes no Gradle arguments.' >&2
    exit 1
fi
AOSP_ROOT="${AOSP_ROOT:-$(cd .. && pwd)}"
TOOLS="$AOSP_ROOT/prebuilts/sdk/tools/linux/bin"
ANDROID_JAR="$AOSP_ROOT/prebuilts/sdk/36/public/android.jar"
OUT="$PWD/build/native"
KEYSTORE="$PWD/build/offline/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo 'Restore the existing signing key at build/offline/debug.keystore.' >&2
    exit 1
fi
VERSION_CODE="$(sed -n 's/^versionCode=//p' version.properties)"
VERSION_NAME="$(sed -n 's/^versionName=//p' version.properties)"
if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]] || [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo 'Invalid version.properties values.' >&2
    exit 1
fi
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated/com/autodroid"
sed 's/<manifest /<manifest package="com.autodroid" /' app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/compiled-res.zip"
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" --java "$OUT/generated" \
    -o "$OUT/resources.apk" --min-sdk-version 26 --target-sdk-version 36 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME-native" "$OUT/compiled-res.zip"
cat > "$OUT/generated/com/autodroid/BuildConfig.java" <<JAVA
package com.autodroid;
public final class BuildConfig {
    public static final boolean DEBUG = false;
    public static final String APPLICATION_ID = "com.autodroid";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String VERSION_NAME = "$VERSION_NAME-native";
}
JAVA
rg --files app/src/main/java -g '*.java' | rg -v '/(MainActivity|DialogUi)\.java$' > "$OUT/sources.txt"
rg --files native-app/src/main/java "$OUT/generated" -g '*.java' >> "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS/core-lambda-stubs.jar" \
    -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
java -cp "$AOSP_ROOT/prebuilts/r8/r8.jar" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 26 \
    --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/resources.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
java -jar "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" sign --ks "$KEYSTORE" \
    --ks-pass pass:android --key-pass pass:android --out "$OUT/AutoDroid-native.apk" "$OUT/aligned.apk"
java -jar "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" verify --verbose "$OUT/AutoDroid-native.apk"
echo "APK: $OUT/AutoDroid-native.apk"
