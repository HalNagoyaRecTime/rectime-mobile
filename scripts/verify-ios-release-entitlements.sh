#!/bin/bash

set -euo pipefail

usage() {
  echo "Usage: $0 /path/to/RECREATION.xcarchive-or-app" >&2
}

if [ "$#" -ne 1 ]; then
  usage
  exit 64
fi

input_path=$1

if [ -d "$input_path/Products/Applications" ]; then
  app_path=$(find "$input_path/Products/Applications" -maxdepth 1 -type d -name '*.app' -print -quit)
elif [[ "$input_path" == *.app ]] && [ -d "$input_path" ]; then
  app_path=$input_path
else
  echo "error: .xcarchive または .app を指定してください: $input_path" >&2
  exit 64
fi

if [ -z "${app_path:-}" ]; then
  echo "error: Archive内に.appが見つかりません: $input_path" >&2
  exit 1
fi

if [ ! -f "$app_path/embedded.mobileprovision" ]; then
  echo "error: embedded.mobileprovisionがありません。署名済み実機用Archiveを指定してください" >&2
  exit 1
fi

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/rectime-ios-entitlements.XXXXXX")
trap 'rm -rf "$temporary_dir"' EXIT

signed_entitlements="$temporary_dir/signed-entitlements.plist"
profile="$temporary_dir/profile.plist"

/usr/bin/codesign --verify --strict "$app_path"
/usr/bin/codesign --display --entitlements - --xml "$app_path" > "$signed_entitlements"
/usr/bin/security cms -D -i "$app_path/embedded.mobileprovision" > "$profile"

signed_environment=$(/usr/libexec/PlistBuddy -c 'Print :aps-environment' "$signed_entitlements" 2>/dev/null || true)
profile_environment=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:aps-environment' "$profile" 2>/dev/null || true)

if [ "$signed_environment" != "production" ]; then
  echo "error: 署名済みアプリのaps-environmentがproductionではありません: ${signed_environment:-missing}" >&2
  exit 1
fi

if [ "$profile_environment" != "production" ]; then
  echo "error: Provisioning Profileのaps-environmentがproductionではありません: ${profile_environment:-missing}" >&2
  exit 1
fi

echo "OK: $(basename "$app_path") の署名とProvisioning Profileはproduction APNsに対応しています"
