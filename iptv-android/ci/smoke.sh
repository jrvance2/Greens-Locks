#!/usr/bin/env bash
# Runs on a booted emulator in CI: installs the release APK, opens it and fails if it crashes in the first 30 seconds.
# This is the shrunk (R8) release build, so it also catches code that minification broke.
set -u
APK=app/build/outputs/apk/release/app-release.apk
PKG=com.greenslocks.iptv
[ -n "${BETA_FLAG:-}" ] && PKG=com.greenslocks.iptv.beta

adb install -r "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 30
adb exec-out screencap -p > smoke.png || true

if ! adb shell pidof "$PKG" >/dev/null; then
  echo "the app is not running: it crashed or was killed"
  adb logcat -d | grep -E "AndroidRuntime|FATAL|Process: $PKG" | head -60
  exit 1
fi
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d | grep -A30 "FATAL EXCEPTION" | head -80
  exit 1
fi
echo "smoke test passed: the app started and stayed up"
