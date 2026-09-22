#!/bin/bash
# Build the debug APK and install it on a USB-connected phone.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_ID="com.loony.bodokeyboard"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[dev]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC} $*"; }
fail() { echo -e "${RED}[error]${NC} $*" >&2; exit 1; }

# ─── Parse args ───────────────────────────────────────────────────────────────
CLEAN=false
for arg in "$@"; do
  case $arg in
    --clean) CLEAN=true ;;
    --help)
      echo "Usage: $0 [--clean]"
      echo "  --clean   Run './gradlew clean' first to force a full rebuild"
      exit 0 ;;
    *) fail "Unknown argument: $arg" ;;
  esac
done

# ─── Pre-flight ───────────────────────────────────────────────────────────────
[ -x "$ROOT_DIR/gradlew" ] || fail "gradlew not found at $ROOT_DIR"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f "$ROOT_DIR/local.properties" ]; then
  SDK_DIR=$(grep '^sdk.dir=' "$ROOT_DIR/local.properties" | cut -d'=' -f2- || true)
fi

ADB=""
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
elif [ -n "$SDK_DIR" ] && [ -x "$SDK_DIR/platform-tools/adb" ]; then
  ADB="$SDK_DIR/platform-tools/adb"
fi
[ -n "$ADB" ] || fail "adb not found — add platform-tools to PATH or set ANDROID_HOME"

DEVICES=$("$ADB" devices 2>/dev/null | grep -c "device$" || true)
[ "$DEVICES" -gt 0 ] || fail "No device connected — plug in your phone with USB debugging enabled"

# ─── Build ────────────────────────────────────────────────────────────────────
cd "$ROOT_DIR"
if [ "$CLEAN" = true ]; then
  log "Cleaning previous build outputs..."
  ./gradlew clean
fi

log "Building debug APK..."
./gradlew assembleDebug
[ -f "$APK" ] || fail "Debug build output not found at $APK"

# ─── Install ──────────────────────────────────────────────────────────────────
log "Installing on device..."
if ! "$ADB" install -r "$APK"; then
  warn "Install failed — if a release build is already installed, its signature"
  warn "won't match this debug build. Uninstall it first, then retry:"
  warn "  adb uninstall $APP_ID"
  exit 1
fi
log "Installed $APP_ID (debug)"

echo ""
log "First time? Enable the keyboard on your phone:"
echo "  Settings > System > Languages & input > On-screen keyboard > Manage keyboards"
echo "  IME id: $APP_ID/.BodoIME"

# Usage:
#   ./scripts/install-dev.sh            # build + install debug APK
#   ./scripts/install-dev.sh --clean    # force a full rebuild first
