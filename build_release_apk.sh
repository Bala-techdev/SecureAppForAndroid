#!/usr/bin/env bash
set -e

BUILD_ROOT="/home/balatechdev/Projects/androidApp/SecureAppForAndroid"
DIST_DIR="$BUILD_ROOT/build/outputs/apk/release"
TEMP_DIR="$BUILD_ROOT/build/apk_temp"

rm -rf "$TEMP_DIR" "$DIST_DIR"
mkdir -p "$TEMP_DIR/dex" "$DIST_DIR"

AAPT2="/home/balatechdev/Android/Sdk/build-tools/36.0.0/aapt2"
D8="/home/balatechdev/Android/Sdk/build-tools/36.0.0/d8"
ZIPALIGN="/home/balatechdev/Android/Sdk/build-tools/36.0.0/zipalign"
APKSIGNER="/home/balatechdev/Android/Sdk/build-tools/36.0.0/apksigner"
ANDROID_JAR="/home/balatechdev/Android/Sdk/platforms/android-37.0/android.jar"
BC_JAR="$BUILD_ROOT/app/libs/bcprov-jdk18on-1.85.jar"
KEYSTORE="$BUILD_ROOT/app/vaultzero-release.jks"
KS_PASS="VaultZeroReleaseKey2026!"

echo "=== 1. Compiling Android Resources with AAPT2 ==="
$AAPT2 compile --dir "$BUILD_ROOT/app/src/main/res" -o "$TEMP_DIR/compiled_res.zip"

echo "=== 2. Linking Base APK with AAPT2 ==="
$AAPT2 link \
  -I "$ANDROID_JAR" \
  --manifest "$BUILD_ROOT/app/src/main/AndroidManifest.xml" \
  -o "$TEMP_DIR/base_unaligned.apk" \
  "$TEMP_DIR/compiled_res.zip" \
  --auto-add-overlay

echo "=== 3. Converting Bytecode to DEX with D8 ==="
MERGED_CLASSES="$TEMP_DIR/merged_classes"
mkdir -p "$MERGED_CLASSES"
if [ -d "$BUILD_ROOT/app/build/tmp/kotlin-classes/debug" ]; then
  cp -r "$BUILD_ROOT/app/build/tmp/kotlin-classes/debug/"* "$MERGED_CLASSES/" 2>/dev/null || true
fi
if [ -d "/home/balatechdev/Projects/androidApp/build_test/classes" ]; then
  cp -r "/home/balatechdev/Projects/androidApp/build_test/classes/"* "$MERGED_CLASSES/" 2>/dev/null || true
fi

CLASS_FILES=$(find "$MERGED_CLASSES" -name "*.class" ! -name "*Test*")
$D8 --release --min-api 26 \
  --lib "$ANDROID_JAR" \
  --output "$TEMP_DIR/dex" \
  $CLASS_FILES \
  "$BC_JAR"

echo "=== 4. Packaging DEX files into APK ==="
cd "$TEMP_DIR/dex"
zip -u "$TEMP_DIR/base_unaligned.apk" classes*.dex
cd "$BUILD_ROOT"

echo "=== 5. Aligning APK with zipalign (4-byte alignment) ==="
$ZIPALIGN -p -f 4 "$TEMP_DIR/base_unaligned.apk" "$TEMP_DIR/VaultZero-aligned.apk"

FINAL_APK="$DIST_DIR/VaultZero-v1.0.0-release-signed.apk"

echo "=== 6. Cryptographically Signing Release APK with apksigner ==="
$APKSIGNER sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "vaultzero" \
  --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$FINAL_APK" \
  "$TEMP_DIR/VaultZero-aligned.apk"

echo "=== 7. Verifying APK Digital Signature ==="
$APKSIGNER verify --verbose "$FINAL_APK"

echo ""
echo "=========================================================="
echo "SUCCESS: Signed Release APK successfully created at:"
echo "  $FINAL_APK"
echo "  Size: $(stat -c%s "$FINAL_APK") bytes"
echo "=========================================================="
