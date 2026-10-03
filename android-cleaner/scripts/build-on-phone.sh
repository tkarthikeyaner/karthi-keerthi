#!/data/data/com.termux/files/usr/bin/bash
# Builds and installs Smart Cleaner directly on an Android phone using Termux.
# Usage (inside Termux):
#   bash /storage/emulated/0/Apps/Mobile-Application/scripts/build-on-phone.sh
# Re-run it after every code change; setup steps are skipped once done.
set -euo pipefail

SRC="${SRC:-/storage/emulated/0/Apps/Mobile-Application}"
WORK="$HOME/Mobile-Application"          # Shared storage is noexec, so build from Termux home.
SDK="$HOME/android-sdk"
OUT="/storage/emulated/0/Download/SmartCleaner-debug.apk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ -d /data/data/com.termux ]] || fail "Run this inside the Termux app."
[[ -d "$SRC" ]] || fail "Project not found at $SRC. Run 'termux-setup-storage' first, or set SRC=/path/to/project."

step "1/6 Installing packages (Java 17, aapt2, tools)"
pkg update -y >/dev/null
pkg install -y openjdk-17 aapt2 rsync unzip wget termux-tools >/dev/null \
  || pkg install -y openjdk-17 aapt rsync unzip wget termux-tools >/dev/null \
  || fail "Package install failed. Use Termux from F-Droid/GitHub (the Play Store build is outdated)."
AAPT2="$(command -v aapt2)" || fail "aapt2 not found after install."

step "2/6 Android SDK (platform 35, build-tools 34)"
if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  tmp="$(mktemp -d)"
  wget -q --show-progress -O "$tmp/tools.zip" "$CMDLINE_TOOLS_URL"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
if [[ ! -f "$SDK/platforms/android-35/android.jar" ]]; then
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-35" "build-tools;34.0.0"
fi

step "3/6 Gradle settings for ARM phones"
mkdir -p "$HOME/.gradle"
cat > "$HOME/.gradle/gradle.properties" <<PROPS
# The SDK's aapt2 is x86-only; use Termux's native ARM build.
android.aapt2FromMavenOverride=$AAPT2
# Keep memory within what a phone can give a background app.
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.daemon=false
PROPS

step "4/6 Syncing project into Termux home"
mkdir -p "$WORK"
rsync -a --delete --exclude '.gradle/' --exclude 'build/' --exclude 'app/build/' --exclude 'local.properties' "$SRC/" "$WORK/"
echo "sdk.dir=$SDK" > "$WORK/local.properties"
chmod +x "$WORK/gradlew"

step "5/6 Building debug APK (first run downloads ~400 MB and takes 10-20 min)"
cd "$WORK"
./gradlew --console=plain assembleDebug || fail "Build failed - see the errors above."

step "6/6 Installing"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT"
echo "APK copied to $OUT"
termux-open "$OUT" || echo "Open $OUT from your File Manager to install."
