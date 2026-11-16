# Shared by restart-local.sh and stop-local.sh; REPO/RUNTIME/fail are set by callers.
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
