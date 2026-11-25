#!/bin/bash
# Builds the home screen APK with Google's SDK tools and the JDK only: no Gradle, no libraries.
# Usage: ./build.sh
# Each step prints its name; the script stops at the first step that fails.

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
# JDK 17 and the Android SDK (build-tools 35.0.1, platform android-30). Defaults are Homebrew's paths
# on macOS; set JAVA_HOME and ANDROID_SDK_ROOT to use others.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.1"
ANDROID_JAR="$SDK_ROOT/platforms/android-30/android.jar"
JAVA_BIN="$JAVA_HOME/bin"
SRC="$PROJECT_DIR/src/local/tvhome"
OUT="$PROJECT_DIR/build"
SIGNING_DIR="$PROJECT_DIR/signing"
KEYSTORE="$SIGNING_DIR/tvhome.keystore"
KEYSTORE_PASS_FILE="$SIGNING_DIR/tvhome.pass"
KEY_ALIAS="tvhome"

# Classes with no Android types: these are what the logic tests compile and run.
PURE_SOURCES=(SkyClock SkyPalette ArcGeometry DateLine WatchNextRules PinnedApps AppRules HomePressLog HomeState DockMagnifier MotionTiming StarMap Comet BodyLook WeatherRules SceneRules FlockPath RainField MediaHistoryRules IconTrim YouTubeRules ControlRules MoonRules ContinueHiding SettingsRules GeoRules)

fail() {
    echo "BUILD FAILED at step: $1" >&2
    exit 1
}

step() {
    echo "== $1"
}

clean_output() {
    step "clean"
    rm -rf "$OUT" || fail "clean"
    mkdir -p "$OUT/compiled-res" "$OUT/gen" "$OUT/classes" "$OUT/test-classes" "$OUT/dex" || fail "clean"
}

run_logic_tests() {
    step "logic tests"
    local sources=()
    for name in "${PURE_SOURCES[@]}"; do sources+=("$SRC/$name.java"); done
    "$JAVA_BIN/javac" -d "$OUT/test-classes" "${sources[@]}" \
        "$PROJECT_DIR/test/local/tvhome/LogicTests.java" || fail "logic tests (compile)"
    "$JAVA_BIN/java" -cp "$OUT/test-classes" local.tvhome.LogicTests || fail "logic tests (run)"
}

compile_resources() {
    step "compile resources"
    "$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$OUT/compiled-res" || fail "compile resources"
    "$BUILD_TOOLS/aapt2" link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" \
        --manifest "$PROJECT_DIR/AndroidManifest.xml" --java "$OUT/gen" \
        "$OUT"/compiled-res/*.flat || fail "link resources"
}

compile_java() {
    step "compile java"
    find "$PROJECT_DIR/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt" || fail "list sources"
    "$JAVA_BIN/javac" -source 11 -target 11 -Xlint:-options -Xlint:deprecation -classpath "$ANDROID_JAR" \
        -d "$OUT/classes" @"$OUT/sources.txt" || fail "compile java"
}

convert_to_dex() {
    step "convert to dex"
    find "$OUT/classes" -name '*.class' > "$OUT/classes.txt" || fail "list classes"
    PATH="$JAVA_BIN:$PATH" "$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --release \
        --output "$OUT/dex" @"$OUT/classes.txt" || fail "convert to dex"
}

package_apk() {
    step "package"
    (cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex) || fail "add dex to apk"
    "$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk" || fail "zipalign"
}

ensure_signing_key() {
    if [ -f "$KEYSTORE" ] && [ -f "$KEYSTORE_PASS_FILE" ]; then
        return
    fi
    step "create signing key (first build only; keep signing/ to install updates over this app)"
    mkdir -p "$SIGNING_DIR" || fail "create signing folder"
    LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32 > "$KEYSTORE_PASS_FILE" || fail "create key password"
    chmod 600 "$KEYSTORE_PASS_FILE" || fail "protect key password"
    "$JAVA_BIN/keytool" -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 3072 -validity 20000 -dname "CN=TV Home" \
        -storepass:file "$KEYSTORE_PASS_FILE" -keypass:file "$KEYSTORE_PASS_FILE" >/dev/null 2>&1 \
        || fail "create signing key"
}

sign_apk() {
    step "sign"
    PATH="$JAVA_BIN:$PATH" "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "file:$KEYSTORE_PASS_FILE" \
        --out "$OUT/tvhome.apk" "$OUT/aligned.apk" || fail "sign"
    PATH="$JAVA_BIN:$PATH" "$BUILD_TOOLS/apksigner" verify "$OUT/tvhome.apk" || fail "verify signature"
}

clean_output
run_logic_tests
compile_resources
compile_java
convert_to_dex
package_apk
ensure_signing_key
sign_apk
echo "BUILD OK: $OUT/tvhome.apk ($(wc -c < "$OUT/tvhome.apk" | tr -d ' ') bytes)"
