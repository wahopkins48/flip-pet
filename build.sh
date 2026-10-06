#!/usr/bin/env bash
# Builds build/flip-pet.apk with plain SDK tools (no Gradle).
# The pet is entirely self-contained: no server, no secrets, no permissions.
# Usage: ./build.sh [install]   — "install" also pushes it to the phone over adb.
#
# Signing reads the keystore passphrase from PET_KS_PASS, which is prompted for
# when unset, so nothing secret is stored in this file. For an existing keystore
# simply export the passphrase once per shell:
#   export PET_KS_PASS=<your passphrase>
#
# DEBUG=1 builds a debuggable variant instead, which is the only way to read the
# save file back off a phone (`adb shell run-as com.wesley.flippet cat files/pet.json`).
# That is a testing convenience only: never hand out that build.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-30/android.jar"
export JAVA_HOME="${JAVA_HOME:-$(mise where java@temurin-17)}"
export PATH="$JAVA_HOME/bin:$PATH"
KEYSTORE="$HOME/.android/flipplayer.keystore"
ADB="${ADB:-$(command -v adb || echo "$SDK/platform-tools/adb")}"

PET_KS_PASS="${PET_KS_PASS:-}"
if [ -z "$PET_KS_PASS" ]; then
    if [ ! -t 0 ]; then
        echo "error: PET_KS_PASS is unset and there is no terminal to prompt on" >&2
        exit 1
    fi
    read -r -s -p "Keystore passphrase for $KEYSTORE: " PET_KS_PASS || true
    echo >&2
    export PET_KS_PASS
fi

rm -rf build && mkdir -p "build/gen" build/classes build/dex

MANIFEST=AndroidManifest.xml
SUFFIX=""
if [ "${DEBUG:-0}" = "1" ]; then
    SUFFIX="-debug"
    MANIFEST=build/AndroidManifest-debug.xml
    sed 's/android:allowBackup="false"/android:allowBackup="false" android:debuggable="true"/' \
        AndroidManifest.xml > "$MANIFEST"
fi

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o "build/unsigned$SUFFIX.apk" -I "$JAR" --manifest "$MANIFEST" \
    --java build/gen --min-sdk-version 26 --target-sdk-version 29 build/res.zip

javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
    -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

(cd build/dex && zip -qj "../unsigned$SUFFIX.apk" classes.dex)
"$BT/zipalign" -f 4 "build/unsigned$SUFFIX.apk" "build/aligned$SUFFIX.apk"

if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    echo "Creating $KEYSTORE"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$PET_KS_PASS" -keypass "$PET_KS_PASS" \
        -alias flipplayer -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Flip Apps" >/dev/null 2>&1
fi
# env: keeps the passphrase off the command line, where ps could read it.
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:PET_KS_PASS \
    --out "build/flip-pet$SUFFIX.apk" "build/aligned$SUFFIX.apk"
echo "Built build/flip-pet$SUFFIX.apk"

if [ "${1:-}" = "install" ]; then
    "$ADB" install -r "build/flip-pet$SUFFIX.apk"
fi
