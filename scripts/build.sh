#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
AOSP_ROOT="${AOSP_ROOT:-$(cd .. && pwd)}"
TOOLS="$AOSP_ROOT/prebuilts/sdk/tools/linux/bin"
ANDROID_JAR="$AOSP_ROOT/prebuilts/sdk/36/public/android.jar"
OUT="$PWD/build/offline"
mkdir -p "$OUT/classes" "$OUT/dex"
sed 's/<manifest /<manifest package="com.autodroid" /' app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/compiled-res.zip"
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" -o "$OUT/resources.apk" --min-sdk-version 26 --target-sdk-version 36 --version-code 19 --version-name 0.5.1 "$OUT/compiled-res.zip"
find app/src/main/java -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS/core-lambda-stubs.jar" -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
java -cp "$AOSP_ROOT/prebuilts/r8/r8.jar" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/resources.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$OUT/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=AutoDroid,C=CN' -keyalg RSA -keysize 2048 -validity 10000
fi
java -jar "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$OUT/AutoDroid-debug.apk" "$OUT/aligned.apk"
java -jar "$AOSP_ROOT/prebuilts/sdk/tools/linux/lib/apksigner.jar" verify --verbose "$OUT/AutoDroid-debug.apk"
echo "APK: $OUT/AutoDroid-debug.apk"
