# Shared startup/readiness functions. REPO, RUNTIME, fail and process helpers must be defined.
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
