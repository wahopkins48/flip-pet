#!/usr/bin/env bash
# Plain SDK build. preview uses a separate app ID and never overwrites a real pet.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
JAR="$SDK/platforms/${ANDROID_PLATFORM:-android-30}/android.jar"
if [ -z "${JAVA_HOME:-}" ] && command -v mise >/dev/null; then export JAVA_HOME="$(mise where java@temurin-17)"; fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
ADB="${ADB:-$SDK/platform-tools/adb}"
MODE="${1:-release}"
if [ "$MODE" = install ]; then MODE=release; fi
if [ "${DEBUG:-0}" = 1 ]; then MODE=preview; fi
case "$MODE" in release|unsigned|preview) ;; *) echo 'Usage: ./build.sh [unsigned|preview|install] [install]' >&2; exit 1;; esac
for tool in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner"; do
    if [ ! -x "$tool" ]; then echo "Missing SDK tool: $tool" >&2; exit 1; fi
done
if [ ! -f "$JAR" ]; then echo "Missing platform: $JAR" >&2; exit 1; fi
OUT="build/$MODE"
mkdir -p "$OUT"
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
MANIFEST=AndroidManifest.xml
if [ "$MODE" = preview ]; then
    MANIFEST="$OUT/AndroidManifest.xml"
    sed -e 's/package="com.wesley.flippet"/package="com.wesley.flippet.preview"/' \
        -e 's/android:name="\./android:name="com.wesley.flippet./g' \
        -e 's/android:label="@string\/app_name"/android:label="Elderflip Preview"/' \
        -e 's/android:allowBackup="false"/android:allowBackup="false" android:debuggable="true"/' \
        AndroidManifest.xml > "$MANIFEST"
fi
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest "$MANIFEST" \
    --java "$OUT/gen" --custom-package com.wesley.flippet --min-sdk-version 26 --target-sdk-version 29 "$OUT/res.zip"
mapfile -d '' SOURCES < <(find src "$OUT/gen" -name '*.java' -print0)
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d "$OUT/classes" "${SOURCES[@]}"
mapfile -d '' CLASSES < <(find "$OUT/classes" -name '*.class' -print0)
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT/dex" "${CLASSES[@]}"
(cd "$OUT/dex" && zip -qj ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ "$MODE" = unsigned ]; then
    cp "$OUT/aligned.apk" "$OUT/elderflip-unsigned.apk"
    echo "Built $OUT/elderflip-unsigned.apk (sign before installing)"; exit 0
fi
if [ "$MODE" = preview ]; then
    KEYSTORE="$OUT/preview.keystore"; KEY_ALIAS=androiddebugkey
    export PET_KS_PASS=android
    APK="$OUT/elderflip-preview.apk"
else
    KEYSTORE="${PET_KEYSTORE:-$HOME/.android/flipplayer.keystore}"; KEY_ALIAS="${PET_KEY_ALIAS:-flipplayer}"
    APK="$OUT/elderflip.apk"
    if [ -z "${PET_KS_PASS:-}" ]; then
        if [ ! -t 0 ]; then echo 'Set PET_KS_PASS for a release, or build unsigned/preview.' >&2; exit 1; fi
        read -r -s -p 'Keystore passphrase: ' PET_KS_PASS; echo >&2
    fi
    export PET_KS_PASS
fi
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass:env PET_KS_PASS -keypass:env PET_KS_PASS \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Elderflip' >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:PET_KS_PASS --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify "$APK"
echo "Built $APK"
if [ "${1:-}" = install ] || [ "${2:-}" = install ]; then "$ADB" install -r "$APK"; fi
