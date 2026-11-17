#!/bin/bash
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
RUNTIME="$REPO/.local"
mkdir -p "$RUNTIME"
fail() { echo "ERROR: $*; logs: $RUNTIME/backend.log and $RUNTIME/frontend.log" >&2; exit 1; }
[[ $(uname -s) == Darwin ]] || fail 'macOS required.'
for command in lsof curl; do command -v "$command" >/dev/null || fail "Missing $command"; done
mkdir "$RUNTIME/restart.lock" 2>/dev/null || fail 'Another start/stop/restart is running.'
trap 'rmdir "$RUNTIME/restart.lock"' EXIT
source "$REPO/scripts/local-processes.sh"
source "$REPO/scripts/local-servers.sh"
check_port backend 47321
check_port frontend 47322
healthy() {
  local service=$1 port=$2 pid
  [[ -f "$RUNTIME/$service.pid" ]] || return 1
  pid=$(cat "$RUNTIME/$service.pid")
  owned "$service" "$pid" && [[ $(listeners "$port") == "$pid" ]] || return 1
  [[ $(curl --noproxy '*' -fsS --max-time 2 "http://127.0.0.1:$port/startup.txt" 2>/dev/null || true) == "celpip-practice-$service" ]] || return 1
  if [[ $service == backend ]]; then curl --noproxy '*' -fsS --max-time 3 "http://127.0.0.1:$port/api/health" >/dev/null || return 1; fi
}
release_service() {
  local service=$1 port=$2 i
  stop_owned "$service"
  for ((i=0;i<10;i++)); do [[ -z $(listeners "$port") ]] && return; sleep 1; done
  fail "Port $port has not been released."
}
if ! healthy backend 47321; then
  export JAVA_HOME
  JAVA_HOME=$(/usr/libexec/java_home -v 21) || fail 'Java 21 required.'
  release_service backend 47321
  # Builds are explicit via Restart; first-ever Start builds only missing artifacts.
  if [[ ! -f "$REPO/backend/target/celpip-practice-0.0.1-SNAPSHOT.jar" ]]; then
    (cd "$REPO/backend" && ./mvnw -B -ntp -DskipTests package) >"$RUNTIME/backend.log" 2>&1 || fail 'Backend build failed.'
  fi
  start_service backend "$REPO/backend" "$JAVA_HOME/bin/java" -jar "$REPO/backend/target/celpip-practice-0.0.1-SNAPSHOT.jar" --server.address=127.0.0.1 --server.port=47321
  wait_ready backend 47321
  healthy backend 47321 || fail 'Backend started but health check failed.'
fi
if ! healthy frontend 47322; then
  command -v node >/dev/null || fail 'Node.js required.'
  release_service frontend 47322
  if [[ ! -f "$REPO/frontend/node_modules/vite/bin/vite.js" ]]; then
    (cd "$REPO/frontend" && npm ci --no-audit --no-fund) >"$RUNTIME/frontend.log" 2>&1 || fail 'Frontend dependency install failed.'
  fi
  start_service frontend "$REPO/frontend" "$(command -v node)" "$REPO/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 47322 --strictPort
  wait_ready frontend 47322
fi
/usr/bin/open -a 'Google Chrome' 'http://127.0.0.1:47322' || fail 'Servers are ready but Chrome could not be opened.'
echo 'CELPIP ready. Healthy services were reused.'
