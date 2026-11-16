# CELPIP Practice

Local-only Spring Boot and React application. Backend practice execution, imports, scoring, recordings, history and evaluation transfer are implemented. Frontend features remain pending. See [backend API and storage contract](backend/API.md).

## Toolchain

Verified on macOS 26.6.1 (Apple Silicon): Java 21.0.12, system Maven 3.9.16, Node.js 26.7.0, npm 11.19.0. The checked-in Maven wrapper uses Maven 3.10.1. Java 21 is required; Node must satisfy `^20.19.0 || >=22.12.0` (the Vite requirement). No system tool upgrades are performed.

Pinned project versions: Spring Boot 4.1.2, React 19.3.1, TypeScript 7.0.2, Vite 8.3.8. Frontend dependencies are locked in `package-lock.json`; backend versions are managed by the pinned Spring Boot parent. Scaffold sources: [Spring Initializr](https://start.spring.io/) and the official [Vite guide](https://vite.dev/guide/).

## Start or restart on macOS

From the repository root:

```sh
./scripts/restart-local.sh
```

The script resolves its own repository location, selects installed Java 21, checks both dedicated ports, stops only processes whose recorded PID, start time and command match, waits for port release, builds both projects, starts direct Java/Node background processes, checks their static readiness markers, and opens Google Chrome at http://127.0.0.1:47322. It uses TERM first and KILL only after 20 seconds. An unfamiliar listener causes an error before either service is stopped. Do not delete `.local` while servers are running: losing ownership records makes the script refuse to stop them. Concurrent invocations are rejected using a directory lock; an interrupted script may require removing `.local/restart.lock` after confirming no restart is running.

Backend: `127.0.0.1:47321`. Frontend: `127.0.0.1:47322`, with Vite `strictPort` and `/api` proxy to the backend. All backend routes are documented in `backend/API.md`. `/startup.txt` on each server is a static initialization probe, not a database health API. The script verifies the responding listener is the process it started.

Logs (replaced on each restart): `.local/backend.log`, `.local/frontend.log`. PID/identity records also live in `.local/`. Failures report these paths; inspect them before retrying. Builds need dependency access on first use. This is the development launcher; packaged single-server launch and macOS Shortcut registration remain Phase 4 work.

## Build and check

```sh
cd backend
./mvnw -B -ntp verify
cd ../frontend
npm ci
npm run typecheck
npm run lint
npm run build
```

All backend tests use isolated temporary data directories; integration/restart tests use file-based H2. Runtime H2 uses `${CELPIP_DATA_DIR:-$HOME/CELPIP-Data}/database/celpip.mv.db`; the verified default on this Mac is `/Users/vikasvarmapokala/CELPIP-Data`. Set `CELPIP_DATA_DIR` to an absolute external path to override it. Flyway performs versioned schema migrations; Hibernate uses `ddl-auto=validate`. Canonical imports reside in H2 and managed image bytes on disk. H2 console and open-in-view are disabled. Never use learner data for tests. For backup/restore, stop the application and copy the entire data root; restore only while stopped.

Stop script-started servers safely (repeatable; preserves all data and logs):

```sh
./scripts/stop-local.sh
```

For manual foreground development, run `./mvnw spring-boot:run` in `backend/` and `npm run dev` in `frontend/` in separate terminals. Stop those processes with Ctrl-C. The restart script deliberately refuses to terminate manually started listeners without its ownership records. To stop script-started services manually, inspect each PID and command from `.local/*.pid` with `ps`, then send TERM to those verified PIDs only; never kill processes by name.

Completed and verified tasks must each have their own Git commit and push, with remote-head verification. Do not force-push.
