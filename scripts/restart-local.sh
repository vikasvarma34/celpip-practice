#!/bin/bash
set -euo pipefail
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
listeners() { lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -u || true; }
identity() { ps -p "$1" -o lstart= -o command=; }
owned() {
  local service=$1 pid=$2 saved
  [[ -f "$RUNTIME/$service.pid" && -f "$RUNTIME/$service.identity" ]] || return 1
  [[ $(cat "$RUNTIME/$service.pid") == "$pid" ]] || return 1
  saved=$(identity "$pid") || return 1
  [[ "$saved" == "$(cat "$RUNTIME/$service.identity")" ]]
}
check_port() {
  local service=$1 port=$2 pid
  for pid in $(listeners "$port"); do
    owned "$service" "$pid" || fail "Unfamiliar listener on port $port (PID $pid): $(ps -p "$pid" -o command=). No processes stopped."
  done
}
stop_owned() {
  local service=$1 pid i
  [[ -f "$RUNTIME/$service.pid" ]] || return 0
  pid=$(cat "$RUNTIME/$service.pid")
  if owned "$service" "$pid"; then
    echo "Stopping $service (PID $pid)..."
    kill -TERM "$pid" 2>/dev/null || true
    for ((i=0; i<20; i++)); do
      owned "$service" "$pid" || break
      sleep 1
    done
    if owned "$service" "$pid"; then
      echo "$service did not stop gracefully; forcing PID $pid."
      kill -KILL "$pid" 2>/dev/null || true
    fi
  fi
  rm -f "$RUNTIME/$service.pid" "$RUNTIME/$service.identity"
}
# Preflight both ports before stopping either service.
check_port backend 47321
check_port frontend 47322
stop_owned backend
stop_owned frontend
for port in 47321 47322; do
  for ((i=0; i<10; i++)); do [[ -z $(listeners "$port") ]] && break; sleep 1; done
  [[ -z $(listeners "$port") ]] || fail "Port $port has not been released."
done
# Build before starting; direct Java/Node processes avoid orphaned Maven/npm children.
echo 'Building backend and frontend...'
(cd "$REPO/backend" && ./mvnw -B -ntp package) >"$RUNTIME/backend.log" 2>&1 || fail 'Backend build failed.'
(cd "$REPO/frontend" && npm ci --no-audit --no-fund && npm run build) >"$RUNTIME/frontend.log" 2>&1 || fail 'Frontend build failed.'
start_service() {
  local service=$1 directory=$2 pid
  shift 2
  (cd "$directory" && exec nohup "$@") >>"$RUNTIME/$service.log" 2>&1 </dev/null &
  pid=$!
  echo "$pid" >"$RUNTIME/$service.pid"
  # Allow the subshell to exec before recording the command and start time.
  sleep 1
  identity "$pid" >"$RUNTIME/$service.identity" || fail "$service exited at startup."
}
start_service backend "$REPO/backend" "$JAVA_HOME/bin/java" -jar "$REPO/backend/target/celpip-practice-0.0.1-SNAPSHOT.jar" --server.address=127.0.0.1 --server.port=47321
start_service frontend "$REPO/frontend" "$(command -v node)" "$REPO/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 47322 --strictPort
wait_ready() {
  local service=$1 port=$2 i pid
  pid=$(cat "$RUNTIME/$service.pid")
  for ((i=0; i<60; i++)); do
    owned "$service" "$pid" || fail "$service exited before becoming ready."
    if [[ $(curl --noproxy '*' -fsS --max-time 2 "http://127.0.0.1:$port/startup.txt" 2>/dev/null || true) == "celpip-practice-$service" ]]; then
      [[ $(listeners "$port") == "$pid" ]] || fail "Unexpected listener on $port."
      echo "$service ready at http://127.0.0.1:$port (PID $pid)"
      return
    fi
    sleep 1
  done
  fail "$service did not become ready within 60 seconds."
}
wait_ready backend 47321
wait_ready frontend 47322
/usr/bin/open -a 'Google Chrome' 'http://127.0.0.1:47322' || fail 'Servers are ready, but Google Chrome could not be opened.'
echo "Startup complete. Logs: $RUNTIME/backend.log and $RUNTIME/frontend.log"
