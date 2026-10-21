#!/usr/bin/env bash
# Builds the KernelSU/Magisk module zip that installs MD3 Agent as a system app.
# Usage: tools/ksu-module/build.sh [path/to/app.apk]
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
apk="${1:-$root/app/build/outputs/apk/debug/app-debug.apk}"

[ -f "$apk" ] || { echo "APK not found: $apk (run ./gradlew assembleDebug first)" >&2; exit 1; }

cp "$apk" "$here/module/system/priv-app/MD3Agent/MD3Agent.apk"

out="$here/md3agent-system.zip"
rm -f "$out"
( cd "$here/module" && zip -qr9 "$out" . -x '.*' )
echo "Built $out"
