#!/bin/bash
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
RUNTIME="$REPO/.local"
fail() { echo "ERROR: $*" >&2; exit 1; }
source "$REPO/scripts/local-processes.sh"
root=''
# The running backend's open process lock is the authoritative resolved data root.
if [[ -f "$RUNTIME/backend.pid" ]]; then
  pid=$(cat "$RUNTIME/backend.pid")
  if owned backend "$pid"; then
    while IFS= read -r line; do
      case "$line" in n*/.process.lock) root=${line#n}; root=${root%/.process.lock};; esac
    done < <(/usr/sbin/lsof -a -p "$pid" -Fn 2>/dev/null || true)
  fi
fi
if [[ -z "$root" && -f "$RUNTIME/backend.log" ]]; then
  root=$(sed -n 's/.*CELPIP data directory: //p' "$RUNTIME/backend.log" | tail -1)
fi
[[ -n "$root" && -d "$root" ]] || fail 'No confirmed data directory found. Start CELPIP once, then retry.'
/usr/bin/open -a Finder "$root"
