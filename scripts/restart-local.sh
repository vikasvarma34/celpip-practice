#!/bin/bash
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
RUNTIME="$REPO/.local"
mkdir -p "$RUNTIME"
fail() { echo "ERROR: $*" >&2; echo "Logs: $RUNTIME/backend.log and $RUNTIME/frontend.log" >&2; exit 1; }
[[ $(uname -s) == Darwin ]] || fail 'This launcher requires macOS.'
for command in lsof curl node npm java; do command -v "$command" >/dev/null || fail "Missing command: $command"; done
export JAVA_HOME
JAVA_HOME=$(/usr/libexec/java_home -v 21) || fail 'Java 21 is required.'
# Serialize restarts; never guess whether an existing lock is safe to remove.
mkdir "$RUNTIME/restart.lock" 2>/dev/null || fail "Another restart is running; if interrupted, remove $RUNTIME/restart.lock after checking."
trap 'rmdir "$RUNTIME/restart.lock"' EXIT
source "$REPO/scripts/local-processes.sh"
source "$REPO/scripts/local-servers.sh"
stop_all
# Build before starting; direct Java/Node processes avoid orphaned Maven/npm children.
echo 'Building backend and frontend...'
(cd "$REPO/backend" && ./mvnw -B -ntp package) >"$RUNTIME/backend.log" 2>&1 || fail 'Backend build failed.'
(cd "$REPO/frontend" && npm ci --no-audit --no-fund && npm run build) >"$RUNTIME/frontend.log" 2>&1 || fail 'Frontend build failed.'
start_service backend "$REPO/backend" "$JAVA_HOME/bin/java" -jar "$REPO/backend/target/celpip-practice-0.0.1-SNAPSHOT.jar" --server.address=127.0.0.1 --server.port=47321
start_service frontend "$REPO/frontend" "$(command -v node)" "$REPO/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 47322 --strictPort
wait_ready backend 47321
wait_ready frontend 47322
/usr/bin/open -a 'Google Chrome' 'http://127.0.0.1:47322' || fail 'Servers are ready, but Google Chrome could not be opened.'
echo "Startup complete. Logs: $RUNTIME/backend.log and $RUNTIME/frontend.log"
