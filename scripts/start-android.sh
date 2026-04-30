#!/bin/bash
set -e

find_emulator() {
  for candidate in \
    "${ANDROID_HOME}/emulator/emulator" \
    "$HOME/Library/Android/sdk/emulator/emulator" \
    "$HOME/Android/Sdk/emulator/emulator"
  do
    [ -f "$candidate" ] && echo "$candidate" && return
  done
  which emulator 2>/dev/null
}

EMULATOR=$(find_emulator)
if [ -z "$EMULATOR" ]; then
  echo "Error: emulator not found. Set ANDROID_HOME or add emulator to PATH." >&2
  exit 1
fi

AVD=$("$EMULATOR" -list-avds 2>/dev/null | head -1)
if [ -z "$AVD" ]; then
  echo "Error: No AVDs found. Create one in Android Studio." >&2
  exit 1
fi

echo "Starting AVD: $AVD"
"$EMULATOR" -avd "$AVD"
