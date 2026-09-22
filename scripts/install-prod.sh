#!/bin/bash
# Build a signed release APK and install it on a USB-connected phone.
#
# The keystore password is never stored anywhere — it's prompted for on every
# run and only kept in memory for the duration of the script.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$ROOT_DIR/app"
APP_ID="com.loony.bodokeyboard"
KEYSTORE="$APP_DIR/bodokeyboard-release.keystore"
KEY_ALIAS="${KEY_ALIAS:-bodo-dictionary}"
UNSIGNED_APK="$APP_DIR/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_APK="$APP_DIR/build/outputs/apk/release/app-release-aligned.apk"
OUTPUT_APK="$APP_DIR/build/outputs/apk/release/app-release.apk"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[prod]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC} $*"; }
fail() { echo -e "${RED}[error]${NC} $*" >&2; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
CLEAN=false
INSTALL=true
for arg in "$@"; do
  case $arg in
    --clean)      CLEAN=true ;;
    --no-install) INSTALL=false ;;
    --help)
      echo "Usage: $0 [--clean] [--no-install]"
      echo "  --clean       Run './gradlew clean' first to force a full rebuild"
      echo "  --no-install  Build + sign only, skip installing on device"
      echo ""
      echo "Env overrides:"
      echo "  KEY_ALIAS   Keystore alias to sign with (default: bodo-dictionary)"
      echo ""
      echo "If ./.env defines KEYSTORE_PASSWORD (and optionally KEY_PASSWORD), it's"
      echo "used instead of prompting."
      exit 0 ;;
    *) fail "Unknown argument: $arg" ;;
  esac
done

# ─── Pre-flight ───────────────────────────────────────────────────────────────
[ -x "$ROOT_DIR/gradlew" ] || fail "gradlew not found at $ROOT_DIR"
[ -f "$KEYSTORE" ] || fail "Keystore not found at $KEYSTORE"
command -v java    >/dev/null 2>&1 || fail "java not found"
command -v keytool >/dev/null 2>&1 || fail "keytool not found — install a JDK"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f "$ROOT_DIR/local.properties" ]; then
  SDK_DIR=$(grep '^sdk.dir=' "$ROOT_DIR/local.properties" | cut -d'=' -f2- || true)
fi

BUILD_TOOLS=""
if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/build-tools" ]; then
  BUILD_TOOLS=$(find "$SDK_DIR/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)
fi

APKSIGNER=""
if command -v apksigner >/dev/null 2>&1; then
  APKSIGNER="apksigner"
elif [ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/apksigner" ]; then
  APKSIGNER="$BUILD_TOOLS/apksigner"
fi
[ -n "$APKSIGNER" ] || fail "apksigner not found — add build-tools to PATH or set ANDROID_HOME"

ZIPALIGN=""
if command -v zipalign >/dev/null 2>&1; then
  ZIPALIGN="zipalign"
elif [ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/zipalign" ]; then
  ZIPALIGN="$BUILD_TOOLS/zipalign"
fi
[ -n "$ZIPALIGN" ] || fail "zipalign not found — add build-tools to PATH or set ANDROID_HOME"

# ─── Keystore password ────────────────────────────────────────────────────────
# Picked up from ./.env (KEYSTORE_PASSWORD / KEY_PASSWORD) when present,
# otherwise prompted interactively. Either way it's only ever kept in memory.
if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

if [ -n "${KEYSTORE_PASSWORD:-}" ]; then
  log "Using keystore password from .env"
  STORE_PASS="$KEYSTORE_PASSWORD"
else
  read -r -s -p "Keystore password for '$KEY_ALIAS': " STORE_PASS; echo
  [ -n "$STORE_PASS" ] || fail "Store password cannot be empty"
fi

if [ -n "${KEY_PASSWORD:-}" ]; then
  KEY_PASS="$KEY_PASSWORD"
elif [ -n "${KEYSTORE_PASSWORD:-}" ]; then
  KEY_PASS="$STORE_PASS"
else
  read -r -s -p "Key password (press Enter to reuse the store password): " KEY_PASS; echo
  KEY_PASS="${KEY_PASS:-$STORE_PASS}"
fi
unset KEYSTORE_PASSWORD KEY_PASSWORD

keytool -list -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -storepass "$STORE_PASS" >/dev/null 2>&1 \
  || fail "Wrong password, or alias '$KEY_ALIAS' not found in $KEYSTORE"

# ─── Gradle build ─────────────────────────────────────────────────────────────
cd "$ROOT_DIR"
if [ "$CLEAN" = true ]; then
  log "Cleaning previous build outputs..."
  ./gradlew clean
fi

log "Building release APK..."
./gradlew assembleRelease
[ -f "$UNSIGNED_APK" ] || fail "Unsigned build output not found at $UNSIGNED_APK"

# ─── Align + sign ─────────────────────────────────────────────────────────────
log "Aligning APK..."
rm -f "$ALIGNED_APK"
"$ZIPALIGN" -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

log "Signing APK..."
"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$OUTPUT_APK" "$ALIGNED_APK"
unset STORE_PASS KEY_PASS

log "Verifying signature..."
"$APKSIGNER" verify --verbose "$OUTPUT_APK" 2>&1 | grep -E "Verified|error" || true

SIZE=$(du -h "$OUTPUT_APK" | cut -f1)
echo ""
log "Build complete!"
echo -e "  File : ${GREEN}${OUTPUT_APK}${NC}"
echo -e "  Size : ${GREEN}${SIZE}${NC}"
echo ""

# ─── Install on device ────────────────────────────────────────────────────────
if [ "$INSTALL" = true ]; then
  ADB=""
  if command -v adb >/dev/null 2>&1; then
    ADB="adb"
  elif [ -n "$SDK_DIR" ] && [ -x "$SDK_DIR/platform-tools/adb" ]; then
    ADB="$SDK_DIR/platform-tools/adb"
  fi
  [ -n "$ADB" ] || fail "adb not found — add platform-tools to PATH or set ANDROID_HOME"

  DEVICES=$("$ADB" devices 2>/dev/null | grep -c "device$" || true)
  if [ "$DEVICES" -gt 0 ]; then
    log "Installing on device..."
    if ! "$ADB" install -r "$OUTPUT_APK"; then
      warn "Install failed — if a debug build is already installed, its signature"
      warn "won't match this release build. Uninstall it first, then retry:"
      warn "  adb uninstall $APP_ID"
      exit 1
    fi
    log "Installed $APP_ID (release)"
    echo ""
    log "First time? Enable the keyboard on your phone:"
    echo "  Settings > System > Languages & input > On-screen keyboard > Manage keyboards"
    echo "  IME id: $APP_ID/.BodoIME"
  else
    warn "No USB device connected — skipping install"
    log "To install manually: adb install -r $OUTPUT_APK"
  fi
fi

# Usage:
#   ./scripts/install-prod.sh                # build + sign + install release APK
#   ./scripts/install-prod.sh --no-install   # build + sign only
#   ./scripts/install-prod.sh --clean        # force a full rebuild first
#   KEY_ALIAS=other-alias ./scripts/install-prod.sh
