#!/bin/bash
set -e

find_adb() {
  for candidate in \
    "${ANDROID_HOME}/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb"
  do
    [ -f "$candidate" ] && echo "$candidate" && return
  done
  which adb 2>/dev/null
}

ADB=$(find_adb)
if [ -z "$ADB" ]; then
  echo "Error: adb not found. Set ANDROID_HOME or add platform-tools to PATH." >&2
  exit 1
fi

# Get connected devices (emulators + real devices)
DEVICE_LINES=$("$ADB" devices | tail -n +2 | grep -E $'\tdevice$')
DEVICE_COUNT=$(echo "$DEVICE_LINES" | grep -c "." 2>/dev/null || echo 0)
[ -z "$DEVICE_LINES" ] && DEVICE_COUNT=0

if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "Error: No devices connected. Start an emulator or connect a real device." >&2
  exit 1
fi

ADB_ARGS=()
if [ "$DEVICE_COUNT" -gt 1 ]; then
  # Prefer real device (emulators are listed as emulator-XXXX)
  SERIAL=$(echo "$DEVICE_LINES" | grep -v "^emulator-" | awk '{print $1}' | head -1)
  [ -z "$SERIAL" ] && SERIAL=$(echo "$DEVICE_LINES" | awk '{print $1}' | head -1)
  echo "Multiple devices found, using: $SERIAL"
  ADB_ARGS=(-s "$SERIAL")
fi

echo "Building and installing..."
./gradlew :composeApp:installDebug

echo "Launching app..."
"$ADB" "${ADB_ARGS[@]}" shell am start -n com.rectime.mobile/.MainActivity
