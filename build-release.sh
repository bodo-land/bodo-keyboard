#!/bin/bash
set -euo pipefail

# ─── Config ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/app"
KEYSTORE="$APP_DIR/bodokeyboard-release.keystore"
KEY_ALIAS="bodo-dictionary"
STORE_PASS="bodo@2025"
KEY_PASS="bodo@2025"
UNSIGNED_APK="$APP_DIR/build/outputs/apk/release/app-release-unsigned.apk"
OUTPUT_APK="$APP_DIR/build/outputs/apk/release/app-release.apk"
OUTPUT_AAB="$APP_DIR/build/outputs/bundle/release/app-release.aab"

# ─── Helpers ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[build]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}  $*"; }
fail() { echo -e "${RED}[error]${NC} $*" >&2; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
BUILD_TYPE="apk"   # apk | aab
INSTALL=true
CLEAN=false

for arg in "$@"; do
  case $arg in
    --aab)         BUILD_TYPE="aab" ;;
    --install)     INSTALL=true ;;
    --no-install)  INSTALL=false ;;
    --clean)       CLEAN=true ;;
    --help)
      echo "Usage: $0 [--aab] [--install] [--no-install] [--clean]"
      echo "  --aab          Build AAB for Play Store instead of APK"
      echo "  --install      Install APK on connected USB device after build (default)"
      echo "  --no-install   Skip USB install even if device is connected"
      echo "  --clean        Run './gradlew clean' first to force a full rebuild"
      echo "                 (without this, Gradle's incremental build reuses"
      echo "                 cached outputs, which can mask stale resources/code)"
      exit 0 ;;
    *) fail "Unknown argument: $arg" ;;
  esac
done

# ─── Pre-flight ───────────────────────────────────────────────────────────────
log "Checking prerequisites..."
command -v java    >/dev/null 2>&1 || fail "java not found"
command -v keytool >/dev/null 2>&1 || fail "keytool not found — install JDK"
[ -d "$APP_DIR" ] || fail "Android app module not found at $APP_DIR"
[ -x "$SCRIPT_DIR/gradlew" ] || fail "gradlew not found at $SCRIPT_DIR"

APKSIGNER=""
if command -v apksigner >/dev/null 2>&1; then
  APKSIGNER="apksigner"
elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
  APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name "apksigner" | sort -V | tail -1)
fi
[ -n "$APKSIGNER" ] || fail "apksigner not found — add it to PATH or set ANDROID_HOME"

# ─── Keystore ─────────────────────────────────────────────────────────────────
if [ ! -f "$KEYSTORE" ]; then
  log "Keystore not found — generating..."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=BodoDictionary, OU=Mobile, O=BodoDictionary, L=Guwahati, S=Assam, C=IN"
  log "Keystore created at $KEYSTORE"
else
  log "Using existing keystore at $KEYSTORE"
fi

keytool -list -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
  -storepass "$STORE_PASS" >/dev/null 2>&1 \
  || fail "Keystore check failed — wrong password or missing alias '$KEY_ALIAS'"

# ─── Gradle build ─────────────────────────────────────────────────────────────
cd "$SCRIPT_DIR"

if [ "$CLEAN" = true ]; then
  log "Cleaning previous build outputs..."
  ./gradlew clean
fi

if [ "$BUILD_TYPE" = "aab" ]; then
  log "Building release AAB (Play Store)..."
  ./gradlew bundleRelease
  OUTPUT="$OUTPUT_AAB"
else
  log "Building release APK..."
  ./gradlew assembleRelease
  # app/build.gradle.kts has no signingConfig, so Gradle emits an
  # unsigned APK — sign it here with the release keystore.
  [ -f "$UNSIGNED_APK" ] || fail "Unsigned build output not found at $UNSIGNED_APK"
  log "Signing APK..."
  "$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" \
    --out "$OUTPUT_APK" "$UNSIGNED_APK"
  OUTPUT="$OUTPUT_APK"
fi

# ─── Result ───────────────────────────────────────────────────────────────────
[ -f "$OUTPUT" ] || fail "Build output not found at $OUTPUT"

SIZE=$(du -h "$OUTPUT" | cut -f1)
echo ""
log "Build complete!"
echo -e "  File : ${GREEN}${OUTPUT}${NC}"
echo -e "  Size : ${GREEN}${SIZE}${NC}"
echo ""

# ─── Signature verify ─────────────────────────────────────────────────────────
if [ "$BUILD_TYPE" = "apk" ]; then
  log "Verifying APK signature..."
  "$APKSIGNER" verify --verbose "$OUTPUT" 2>&1 | grep -E "Verified|error" || true
fi

# ─── Install on device ────────────────────────────────────────────────────────
if [ "$BUILD_TYPE" = "apk" ] && [ "$INSTALL" = true ]; then
  ADB=""
  if command -v adb >/dev/null 2>&1; then
    ADB="adb"
  elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB="$ANDROID_HOME/platform-tools/adb"
  fi

  [ -n "$ADB" ] || fail "adb not found — add platform-tools to PATH or set ANDROID_HOME"

  DEVICES=$("$ADB" devices 2>/dev/null | grep -c "device$" || true)
  if [ "$DEVICES" -gt 0 ]; then
    log "Installing APK on connected device..."
    "$ADB" install -r "$OUTPUT"
    log "Installed successfully"
  else
    warn "No USB device connected — skipping install"
    log "To install manually: adb install -r $OUTPUT"
  fi
fi

# Usage:
#   ./build-release.sh                          # build APK + auto-install if USB connected
#   ./build-release.sh --aab                    # build AAB for Play Store
#   ./build-release.sh --no-install             # build only, skip install
#   ./build-release.sh --clean                  # force a full rebuild (gradlew clean first)
