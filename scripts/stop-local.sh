#!/bin/bash
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
RUNTIME="$REPO/.local"
mkdir -p "$RUNTIME"
fail() { echo "ERROR: $*" >&2; exit 1; }
[[ $(uname -s) == Darwin ]] || fail 'This launcher requires macOS.'
command -v lsof >/dev/null || fail 'Missing command: lsof'
mkdir "$RUNTIME/restart.lock" 2>/dev/null || fail 'Another start/stop operation is running.'
trap 'rmdir "$RUNTIME/restart.lock"' EXIT
source "$REPO/scripts/local-processes.sh"
echo 'CELPIP stopped. Both dedicated ports are free; data and logs are preserved.'
