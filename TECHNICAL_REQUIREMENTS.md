# CELPIP Practice — Technical Requirements

Version 1.0 | 16 November 2026 | Implementation specification | Contract `schemaVersion: 1`

Repository: https://github.com/vikasvarma34/celpip-practice.git

## 1. Objective and delivery approach

Build a small, reliable, attractive CELPIP-General practice simulator for one learner on a MacBook Air (Apple Silicon). It is an exam-like practice environment, not a learning management system or an AI application. The learner's target is Listening 10, Reading 10, Writing 9 and Speaking 9. Daily preparation and subsequent evaluation are coordinated in a separate ChatGPT Project.

- ChatGPT is the principal reasoning model, planner and reviewer. It issues instructions phase by phase. Codex in VS Code implements, runs tests, reports evidence and fixes scoped defects. Do not autonomously implement subsequent phases or reinterpret agreed requirements. Read this document at the beginning of each development session.
- Keep the number of phases small: backend, frontend, integration/testing, final polish/local launch. The desired usable release is November 17, 2026 (a planning target, not a guarantee). Prioritize a reliable core workflow over engineering sophistication or a Java tutorial. Use ordinary Spring Boot annotations and conventional React code. No premature abstractions or unnecessary dependencies.
- For each phase, Codex should inspect the current repository, implement only the assigned scope, run the relevant tests/build, update the implementation status in this file, and report concise changes, commands, failures and remaining blockers. Never mark a phase complete on the basis of code generation alone. Ongoing workflow authorized in Task 02: every completed and verified task requires a separate Git commit and push, with remote-head verification; never force-push. Stop and report if a required commit or push fails.
- This specification is grounded in the actual `PRACTICE_GENERATION_INSTRUCTIONS.md` v1.0 and `COACHING_INSTRUCTIONS.md` v1.0 used in the coaching project. The exact import rules and task definitions are reproduced in Appendix A below, so Codex does not need access to the user's Google Drive or ChatGPT project to implement the simulator. Earlier experimental full Speaking mock ZIPs, including the revised one and the online Task 1 prototype, are not compatibility fixtures; they predate the canonical contract. Reject incompatible imports with useful errors. Do not silently accommodate historical formats.

## 2. Scope and separation of responsibilities

| Component | Responsibilities |
|---|---|
| ChatGPT / Astra | Choose the next practice unit with the learner; research unused free material, check registry and reuse permissions; generate original material when necessary; independently validate answers and images; produce the import file; coach/evaluate after submission. |
| React | Import interface, ready screen, exam-like task layout, timers, drafts, microphone recording, submission, result display, practice history, clipboard export and Finder reveal controls. |
| Spring Boot | Validate and persist packages, manage assets and local attempt/session state, save audio bytes, grade Reading, preserve history, supply stage-appropriate payloads, generate compact evaluation context, reveal known files in Finder. |
| Local H2 + filesystem | Durable question and attempt metadata, drafts, responses, results and assets. |
| Google Drive | External coaching journal, agent corrections and material registry, maintained manually by the coaching agent using its connected tools. |

Supported units: Reading Parts 1–4 (one complete part at a time, never one isolated question), Writing Tasks 1–2 (one task), Speaking Tasks 1–8 (one task except the mandatory 3–4 pair with a single shared image and two responses). Listening is practised externally; there is no Listening component/import format in v1. No full-module/bulk or mixed-module package in v1, even for weekly review. Weekly mock practice can use separately imported units or an external official mock.

No authentication or signup, user accounts, cloud deployment, AI/API integration, automatic transcription, paid services, online image hotlinking, in-app question generation, question-bank selection screens, dashboards with analytics, automatic Google Drive connector, official score conversion engine or background-noise simulator in v1. Use ordinary local audio/background speech outside the app if later wanted. Do not add advanced scalability or enterprise infrastructure. Keep essential localhost/file-handling protections; local use alone does not justify unsafe imports.

## 3. Project structure and local runtime

Single repository, separate frontend and backend directories; use simple tooling, no microservices or Docker requirement.

```text
celpip-practice/
  TECHNICAL_REQUIREMENTS.md
  README.md
  .gitignore
  backend/                 # Spring Boot, Maven wrapper
    pom.xml
    src/main/java/...
    src/main/resources/...
    src/test/...
  frontend/                # React, TypeScript, Vite
    package.json
    package-lock.json
    src/...
  scripts/                 # development restart helper initialized in Task 01
  fixtures/                # synthetic non-practice fixtures for automated tests
```

Recommended baseline (engineering decisions, not user-mandated version numbers): Java 21 LTS, Maven wrapper, a stable compatible Spring Boot version, React with TypeScript/Vite, npm lockfile, Spring Web, Validation, Data JPA, file-based H2 and Spring Boot Test. Inspect installed Java/node/npm before scaffolding; select compatible maintained stable versions, pin them and document exact requirements. No external database, Redis, message broker or framework-heavy UI library needed; use ordinary CSS or a small component approach. Use `@RestController`, `@Service`, repositories, `@Transactional` at the appropriate boundaries and a global error handler; favor constructor injection. Do not divide the code into layers merely for their own sake.

Local application should bind to `127.0.0.1`, reserved backend port 47321, Vite dev port 47322 (`strictPort: true`), using the Vite `/api` proxy to `http://127.0.0.1:47321` in development. Task 01 authorizes `scripts/restart-local.sh` now: resolve the repository, refuse unfamiliar listeners, gracefully stop verified owned processes (force only after timeout), verify port release, build/start both servers in the background, check static readiness markers, and open Google Chrome. Separate logs and PID/start-time/command records live in ignored `.local/`. This development restart mechanism does not complete the Phase 4 packaged launch/Shortcut gate. For daily use prefer a built React bundle served by Spring Boot at one localhost URL; package build or documented simple build-copy step must work from clean checkout. No public interfaces, wildcard cross-origin access or external network dependence for running imported tests. Use appropriate host/origin checks for state-changing local requests and avoid browser caching of unrevealed content. Disable H2 web console by default. Browser microphone on localhost must be tested in the actual Chrome environment. Dev and packaged mode should use the same storage contract.

## 4. Data directory and persistence

Keep every real exercise, response and recording outside the git repository. Suggested fixed default is `~/CELPIP-Data` (expand to absolute path via `user.home`), with a single configurable override such as `CELPIP_DATA_DIR` or a Spring property. Confirm and document the actual resolved path in README/startup logs. Never use the process working directory, Downloads or repository as durable storage. Create directories on first run. Reboots, rebuilds, branch switches and normal restarts must preserve all data.

```text
~/CELPIP-Data/
  database/                # H2 database files
  questions/<packageId>/   # canonical exercise JSON / immutable imported content
  images/<packageId>/      # referenced local image bytes only
  recordings/<packageId>/  # original audio for each attempt
  exports/                 # optional user-requested exports
  staging/                 # bounded temporary imports/uploads, cleanly recoverable
```

Use file-based H2, not in-memory H2 except isolated tests. Store relative managed file references, not absolute paths embedded in imported JSON. Keep large binary assets/audio on disk, not in H2 BLOBs. Small content and response JSON may be stored in H2 or in canonical question files, but choose one authoritative representation and avoid divergent copies. The canonical imported content must remain recoverable for history/review. Store a schema/version marker and use explicit, non-destructive database evolution (a simple migration strategy is enough; do not use `create-drop` on real data). One local process owns the database. No destructive reset from a normal startup or retry. Document backup: stop app, copy entire data root safely, restore only with app stopped. Test with separate temporary data directories; never use live learner data for tests. Ignore local data, recordings, credentials, build output and IDE clutter in git.

### Logical data model (minimum)

Use conventional JPA entities and types; the following is a logical model, not a requirement for excessive tables. JSON content can be a text field where sensible. Use generated UUIDs for sessions/attempts and unique external string IDs for imported packages/exercises. Store timestamps in UTC, display local time; use monotonic elapsed measurements for timing, not calendar subtraction during execution.

- Package: packageId, module, schemaVersion, importedAt, content fingerprint, file manifest/path; one or two ordered exercises. Exercise: globally unique exerciseId, package FK, taskNumber, ordered index, source metadata, immutable original content, Reading answer key (private), fingerprints. Asset references belong to the package; one physical shared scene for Tasks 3/4. Validate associations before writing. No dedicated image entity is necessary unless implementation clearly benefits.
- Practice session: sessionId, packageId, mode (`PRACTICE`, `TIMED`, `EXAM`), status, current exercise/stage, timestamps. Every Start creates a new session; one attempt for each started exercise. Task 3 and 4 link to the same session. No need to store an entire weekly mock as one session.
- Attempt: attemptId, sessionId, exerciseId, sequence/attempt number, mode, status, timestamps, actual elapsed preparation and response duration (milliseconds or seconds, consistently documented), selected Reading options or Writing answer/choice/word count, Task 5 selection ID/manual-vs-auto and actual selection duration, original audio path/media type/byte length, Reading raw result where applicable. Save exact answer, missing choices and deviations, not corrected content. Separate timestamps for capture and durable submission as useful.
- Drafts: persist Reading choices and Writing text/choice periodically and at relevant events; do not manufacture a final submission from a lost browser tab. Store in-progress separately from submitted outcomes. A completed attempt's original response, exercise snapshot and historical result must not be silently changed by corrections or repeats.

Use clear status semantics, e.g. `IN_PROGRESS`, `COMPLETED`, `INTERRUPTED`, `SAVE_FAILED` (recoverable), with session completion only after all expected attempts are durably saved. Record interruption reason where available. No score for an unfinished exercise. A timed expiry with an empty response is a completed submission if successfully saved; flag missing content rather than invent it. A recovered or repeated task is never falsely reported as a previously unseen attempt.

## 5. Import, validation and spoiler prevention

Provide a module entry screen with file upload/drop and optional intentional JSON paste. No list of fresh questions to browse. User obtains one unit from ChatGPT, then imports it. Selecting module first may preselect expected module; reject mismatch clearly. ZIP preferred; accept a standalone JSON file for text-only packages, and pasted JSON with matching separate images when needed (same validated path mapping; never infer assets from names without a match). Do not require absolute file paths from the browser; use a standard file picker/drop. Do not fetch arbitrary server paths or remote URLs.

Appendix A is normative. Strictly parse JSON (including duplicate-key detection, correct numeric types, exact field names, no unknown fields or coercion of strings to integers, no trailing tokens). Validate v1 envelope, allowed module/task/cardinality, ordered task types, required/forbidden keys, strings, unique IDs, exact question groups/labels/counts, answer-key coverage and index ranges, blank-token placement/order, option order/identities and asset references. Match source `ONLINE` to HTTPS URL and `AI_GENERATED` to null; source URL is metadata, never a file-fetch command. No timing fields. Reject unexpected files, executable markup or unsafe paths. Render text as text; no raw HTML injection.

For ZIP imports, allow exactly root `exercises.json` and referenced `images/` files (optional harmless directory entries). Reject archives with traversal, absolute paths, backslashes, duplicate paths/entries, symlinks, nested archives, missing/extra assets, non-image payloads, mismatching extensions/media, corrupt data, extreme sizes/decompression or image dimensions. Validate actual decoded JPEG/PNG bytes and dimensions, not just names. Suggested initial configurable limits: compressed package 30 MiB, total extracted 60 MiB, JSON 2 MiB, individual image 15 MiB, maximum 25 megapixels per image, 16 entries including directory entries, expansion ratio 100:1 per entry after first MiB. These are engineering defaults, not CELPIP rules; verify they accommodate realistic assets and adjust deliberately if needed. Enforce streaming caps before allocating or extracting. Audio-upload cap suggested 50 MiB per attempt, configurable. Only accept explicitly supported audio types/formats from actual browser recording and reject empty/invalid uploads. Do not transcode by default.

Validate in isolated staging first. Check IDs and exact content duplicates, compute canonical SHA-256 fingerprints (see generator guide method summarized below) and asset-byte hashes; check both prompt/passage-only and full content. Content comparison must account for actual referenced image bytes, not only path names, so different images named `scene.jpg` are not mistaken for identical visuals. Exact duplicates may reuse the existing stored immutable unit for repeat practice; ID collision with different content is a conflict, never replace. Use locks/constraints as needed for races. Do not imply hashes detect all semantic duplicates; the external registry is the agent's responsibility. Canonicalization: sorted JSON keys, array order retained, compact UTF-8, strings NFC and LF; exclude package/exercise IDs and source/time metadata, include content plus Reading key; calculate independent image-byte hashes and passage/prompt-only identity.

Only after complete validation install assets and persist records. Coordinate DB transaction with staged file moves and compensating cleanup: filesystem and H2 do not magically share a transaction. If either fails leave no exposed partial package or orphaned visible records; clean staging or reconcile on restart. Use collision-safe managed paths; do not overwrite prior files. Return neutral import receipt only (module, task number(s), count, ID, status). Any errors shown before Start should avoid quoting exercise text or correct answers. Log diagnostic field paths/error codes, not unnecessary learner/private data.

Ready screen: module/task label, count, mode and Start; no previews, source descriptions that spoil content, image thumbnails, correct answers, or future prompts. Backend supplies content only for the active stage. Reading key must not be in initial API payload or static assets/browser cache; Task 5 C and persuasion prompt not included initially; paired Task 4 prompt withheld until Task 3 saves. Serve images only via managed/scoped asset IDs and stage-appropriate access. The import itself exists on the learner's machine and could be inspected intentionally; these controls prevent accidental spoilers, not determined cheating. History may reveal completed/previously seen tasks only when explicitly opened; an unattempted imported unit remains neutral.

## 6. User interface and execution model

Design a polished, calm and readable desktop-first simulator for Mac/Chrome. Use consistent typography, spacing, high contrast, clear countdowns, neutral prompts, keyboard accessible controls, proper focus management and responsive layouts. Keep it clean, not a decorative statistics dashboard. Exam-like interaction takes priority over mimicking branding pixel-for-pixel; no claim of official affiliation. Avoid default browser confirm dialogs if clear in-app confirmation is possible. Errors must be visible and actionable.

Home: Reading, Writing, Speaking, Import, Practice History. Module selection goes directly to import/ready workflow, not filters or a question bank. After import choose practice mode, then Start. Do not reveal content before Start. Clicking a completed history entry may review results, playback, reveal saved recording and deliberately retry the same unit as a new session/attempt. Paired retry starts the pair from Task 3. No bulk imports, no separate question-management system.

### Modes and common timing behavior

- `PRACTICE`: no enforced limit, record actual elapsed prep/response or total task time. Speaking has manual Start/Stop recording; Task 5 selection/prep unlimited, with explicit advance. Reading/Writing manual submit.
- `TIMED`: configured exam-like countdowns, automatic speaking phase transitions/recording stop and automatic Reading/Writing submission at expiry. Manual early submit for Reading/Writing may be supported with confirmation; Speaking follows its countdown. Reattempt later creates a new attempt.
- `EXAM`: strict phase timing, no pausing, restarting or changing committed stage/answers after submission; automatically transition/submit when time expires. Explicit abandon marks interrupted, no in-place retry. Manual early submission for Reading/Writing (if supported) is final. No assistance beyond agreed basic spell-check. No result reveal before submission.

The timing configuration is maintained by application code/config (Appendix A timing table). No user-entered timings in package. Reading estimates are simulator defaults; writing includes all planning/review time. Keep one source of truth on backend, return applicable timing config to frontend. Persist actual durations and mode. Frontend scheduler should derive remaining time from monotonic deadline rather than decrementing a counter once per second; handle throttling, visibility and duplicate callbacks. Backend attempt/session state should enforce ordered stages and idempotent submission, rather than relying only on UI. Do not introduce elaborate server-synchronized real-exam proctoring: local trusted app. Pause no timed session silently. If browser closure or suspension prevents reliable recording/timing, mark interrupted; do not claim a valid exam. Clearly show timer expiry/recording start/stop; audible cue in speaking timed/exam modes, with visible fallback if audio cue cannot play. Asset load and disk-save delays do not consume the learner's prep/response time. Debounce autosaves; disable duplicate submit while save pending; allow safe retry if network/local write fails.

### Reading

Single part on one examination screen with passage/diagram left and questions right, independent scrolling where helpful. Render `TEXT`, labelled `PARAGRAPH`, `IMAGE` and structured `TABLE` faithfully; preserve line breaks, labels, rows/column cells, units and readable images. Display groups in prescribed order. Use four-choice dropdown/selection controls for MULTIPLE_CHOICE and inline dropdown blanks, five A–E choices for PARAGRAPH_MATCH; E means information not stated. No free-text blanks; no option reshuffling. All selections editable before submit; allow unanswered questions. Use stable question IDs and zero-based selected indices. Autosave selections. At submission backend grades one point per correct answer, zero otherwise including unanswered. Return total correct/total and per-question selected/correct option and evidence explanation; visually distinguish incorrect and unanswered. Do not map a single part to an official CELPIP score. If answer key later challenged, preserve original attempt and record defect/correction transparently; do not mutate immutable package history or offer a misleading score. Review original response and explanations after submit; Copy for ChatGPT defaults to wrong/unanswered items and enough passage/diagram evidence to interpret them (include full passage only when needed; never omit necessary qualifiers). Correct-answer reasoning is a separate generator responsibility, not guaranteed by parser.

### Writing

Reuse same plain text editor for both tasks. Display complete email scenario/instructions/bullets or survey title/scenario/instructions/A and B options together with response. Survey choice recorded but not force a choice or word minimum to submit at expiry. Live word counter; target 150–200 words as guidance, no hard cap/minimum. Suggested shared counting method: trim, split on Unicode whitespace, count nonempty tokens, same frontend/backend rule (document limits for hyphenation). Preserve exact typed content. Basic browser spelling assistance enabled (correction to the initial all-off proposal). Disable autocorrect, autocapitalization, grammar/predictive/AI extensions as far as browser supports; cannot guarantee control of third-party browser/OS features, document that limitation. No templates, sample answers, coaching tips or grammar evaluator in UI. Debounced draft persistence with last-saved indication; preserve drafts if interrupted, distinguish resuming as non-fresh practice. On submit save response, selection, word count, elapsed time, mode, ID; no automatic Writing grade. Copy for ChatGPT returns full prompt and bullets/options, original response, selected option and relevant actual timings/conditions only.

### Speaking

Use one shared renderer/recorder with task-specific content and stage logic, not eight separate implementations. Request microphone permission and check `getUserMedia`, `MediaRecorder` and supported MIME type before revealing prompt. Prefer Chrome's supported WebM/Opus if available; select actual supported type using feature detection, not forced extension. Keep the stream ready but do not record preparation/selection; stop tracks on session end/abandon. Use bounded chunks and accumulate/persist safely as appropriate. Handle microphone denial, revocation/disconnect, recorder errors, empty blobs, autoplay restrictions and upload failures explicitly. Verify image loading/decoding before starting the active prep clock. Image stays visible during speaking; fit without cropping; allow a readable full image view without disrupting timer. Capture original unmodified audio. Start on prep expiry in timed/exam mode, stop on response expiry; in practice allow manual controls. Wait for final `dataavailable` and recorder stop event before upload. Do not mark saved/advance until durable backend acknowledgment; retain captured Blob for retry while page remains open. Reload recovery is best effort; do not promise every unsaved audio chunk survives a crash. Persist interruption/current stage and preserve any already saved response. After completion offer playback of stored file, Show in Finder, Copy evaluation context. No automatic transcription or AI scoring.

- Tasks 1, 2, 7: text prompt, ordinary prep and recording; no selectable opinion buttons.
- Tasks 3/4: mandatory pair, one image file, separate attempt IDs, shared session. Task 3 prompt and image only at Start; save first audio then automatically advance, without feedback or preview. Task 4 uses same physical image, new prompt and fresh timers. Practice mode manual start/stop; automatic transition after successful save; unlimited Task 4 preparation. If Task 3 save fails, stop transition and offer retry; if Task 4 fails, keep Task 3 complete, session incomplete. Do not overwrite first recording or reclassify interrupted pair as complete.
- Task 5: stage 1 only prompt and A/B plus images/details. No selection default before user's choice; on expiry lock current chosen option or choose deterministic A if none (document local fallback, not official selection algorithm). Allow explicit confirmation to advance. Store choice, auto/manual flag and actual selection duration. Stage 2 after lock: persuasion prompt and chosen option plus C only, not rejected initial option. No switching after lock. Start prep when both images ready; then one recording. Practice mode unlimited stage times and manual recording. Use shared option renderer and stage-specific backend payload. No predefined correct choice.
- Task 6: scenario and both EITHER/OR alternatives visible concurrently throughout prep/recording. No mandatory choice control, no extra timer/image, one recording. Evaluation infers choice from answer or asks if unclear.
- Task 8: prompt and one unusual image; ordinary image renderer/recording; no extra stages.

## 7. Local recordings, history and evaluation transfer

Maintain recognizable filenames, e.g. `2026-11-16_S-T03-001_Attempt01.webm`, with date in learner's local timezone, stable exercise ID, atomically assigned attempt sequence, and actual MIME-matching extension. File name sanitized, length bounded, collision-safe across sessions (use unique attempt ID suffix if required). Never overwrite on retries or simultaneous writes. Save audio using a staged file, validate nonzero bytes/type, atomically move where supported and persist a reliable attempt record with size/path. Reconcile files/DB on failure; repeat submission with same attempt ID returns existing final record rather than creating another file; changed payload to completed attempt conflict. Do not report success until both file and metadata are committed. Audio served via controlled endpoint for playback, not unrestricted filesystem access.

Show in Finder: macOS backend executes `open -R <absolute verified managed path>` using `ProcessBuilder` argument list (`/usr/bin/open`, `-R`, path). Only given a stored attempt or export ID; resolve path inside configured root and verify file exists/no symlink escape. Never accept shell command or arbitrary filepath from client, never use shell string interpolation. Return clear failure if not macOS or file missing. Do not download an unnecessary duplicate of an already-local recording. Learner can drag original recording into ChatGPT. App cannot directly send data to ChatGPT or access its project; no API integration.

History: simple chronological completed/interrupted attempts and paired-session grouping, module/task label, dates, mode, duration, Reading raw score or completion status. No score inventions for Writing/Speaking. Review prior submitted responses and audio; repeat deliberately without overwriting. Unstarted imports not counted as attempts. Keep enough context to resume after restart; no automatic deletion of real data. No dashboard stats requirement.

Compact transfer (default): clipboard text for prompts/responses/timing, plus Show in Finder for relevant audio and original images. For visual Speaking include actual relevant image alongside context; for pair send scene once with both prompts and recordings when reviewing both. Task 5 needs selected option and C descriptions/images, initial and persuasion instructions, actual stage times; rejected initial image not necessary. Task 6 include both alternatives. Reading includes wrong/unanswered items and necessary full passage/diagram evidence. For complicated cases support explicitly requested ZIP export to `exports/` with only the relevant exercise context, response(s), timings, audio and relevant image assets, not entire DB or history. A completed Reading export may include its revealed answer/explanation. Never include unrelated answers, internal metadata, private paths or future tasks. Distinguish export (evaluation data, not import contract) from import ZIP. A simple "Copy for ChatGPT" and "Show in Finder" for stored assets or export is enough; no automatic API or cloud upload. Text should be readable directly, not giant raw JSON; minimize unnecessary token use without losing evidence.

## 8. Backend HTTP interface and validation boundaries

Suggested small REST surface (implementation may adjust exact route names in phase 1; freeze and document final contract before frontend). JSON responses unless binary/file or multipart. Provide clear consistent error body `{code,message,fieldErrors?}` with safe non-spoiling pre-start messages. Expected 400/422 for invalid structure, 409 conflicts or invalid state, 404 missing item, 413 limits, 500/503 local persistence error. Do not expose stack traces or answer keys in error messages. Use explicit DTOs and never serialize private entities directly to ready/active responses.

| Route (suggested) | Purpose |
|---|---|
| GET `/api/health` | Health and local data readiness; no content |
| GET `/api/config` | Modules, task timing defaults, modes and capabilities |
| POST `/api/imports` | Multipart ZIP/JSON + optional image files, or JSON paste envelope via documented multipart JSON part; validate and return neutral receipt |
| GET `/api/packages/{id}` | Neutral ready metadata only |
| POST `/api/sessions` | Start new session for packageId + mode after UI preflight; return active stage metadata/content |
| GET `/api/sessions/{id}` | Current stage and minimal recovery data; scope content to active stage |
| PATCH `/api/attempts/{id}/draft` | Persist Reading/Writing draft; no grading or solution |
| POST `/api/sessions/{id}/advance` | Validated stage transition where needed (Task 5 selection lock, Speaking prep/record state, paired transition); do not release Task 4 until Task 3 persisted |
| POST `/api/attempts/{id}/submit` | Reading/Writing final JSON or Speaking audio multipart + timings; idempotent completion/graded result where applicable |
| POST `/api/sessions/{id}/interrupt` | Explicit abandon; preserve data and mark status; on startup reconcile stale in-progress sessions |
| GET `/api/assets/{id}` | Controlled scoped image bytes for authorized stage/review in local session; no arbitrary file route |
| GET `/api/attempts/{id}/audio` | Playback of successfully stored audio |
| GET `/api/history` | Neutral concise session/attempt history |
| GET `/api/attempts/{id}/review` | Submitted attempt and revealed results, not earlier |
| GET `/api/attempts/{id}/evaluation-context` | Minimal text/asset references for completed attempt; paired context as applicable |
| POST `/api/exports` | Optional requested compact local evaluation ZIP |
| POST `/api/reveal` | Reveal managed audio/image/export by ID in Finder |

A single shared service can handle transitions; don't create unnecessary controller per task. Enforce session/attempt ownership association (not account authorization), valid state and no premature stage changes; verify durations and selection values, avoid hard numeric coercion. Use server-provided expected stage/config; frontend is responsible for actual recording clocks, not an elaborate distributed timing service. Document how stage-specific assets are addressed without exposing future tasks. Handle stale/duplicate calls without extra attempts or overwritten data. Return Task 3 completion and next-stage info on acknowledged save or via explicit advance, not both as conflicting transitions. Keep runtime and tests independent from Drive/network. Unit tests use synthetic fixtures, not actual unseen practice prompts. Report exact API shapes in README or a compact API section before frontend consumes them.

## 9. Implementation phases and acceptance gates

Work in four coarse phases. This document is the full destination specification, not authorization to execute every phase at once. ChatGPT will give the actual step-by-step prompts; Codex must stop at each agreed gate and report. No need to subdivide into dozens of chats or milestones. Keep setup and test scaffolding proportional to the short schedule.

### Phase 1 — Spring Boot backend

Scope: scaffold backend, fixed external data directory, H2 persistence, canonical DTOs/validation for all 14 task formats, safe import, stage/session/attempt state, Reading scoring, draft saving, audio persistence, playback/asset retrieval, Finder reveal, history and evaluation export endpoints. Deliver API documentation and synthetic fixtures for every supported unit. Run backend build and tests against isolated data directory. Verify data persists across restart, invalid imports leave no partial records, and keys/future stages remain hidden. Do not build frontend in this phase.

### Phase 2 — React frontend

Scope: scaffold clean React UI, import/drop/paste, neutral ready page, mode choice, Reading blocks/questions/results, Writing editor/drafts, reusable Speaking timer/recorder with Task 3–4 pair and Task 5 stages, history and evaluation controls. Use phase-1 actual API contract; no parallel mock API masquerading as integration. Run typecheck, tests and production build. Confirm no unrevealed prompts, assets or keys sent in UI state. Do not introduce coaching or other modules.

### Phase 3 — Integration and testing

Run both together on the Mac/Chrome and exercise all formats using synthetic fixtures. Verify actual microphone permission/recording/save/playback and Finder reveal, not only mocked calls. Test expiry, transitions, retries, reopen/restart, duplicate import, error handling and result correctness. Fix blockers in scope. A backend unit-test pass alone does not establish actual browser audio reliability. Use shortened timing configurations in isolated automated tests only; restore canonical defaults for daily use.

### Phase 4 — Final polish and local launch

Complete responsive polish, accessibility, cleanup and README. Provide one simple local start command/script and a macOS Shortcut compatible launch mechanism, plus browser bookmark workflow: a Chrome `CELPIP` bookmarks folder with a `Start Server` shortcut that starts the local app if not running, waits for the correct health response and opens it, or just opens it if already healthy. A normal HTTP bookmark cannot launch shell commands by itself; document a supported macOS Shortcuts URL/shortcut or equivalent local workflow and any manual registration required. Do not kill arbitrary processes using the same port; detect conflict and report, or stop only a process demonstrably owned by this app when explicitly requested. Keep a startup log in a known location and document stop/restart/backup. Verify launch from stopped and already-running states. If Shortcut registration needs the user's local approval, provide exact steps and report untested parts, not a claimed installed shortcut.

### Minimum acceptance test checklist

| Area | Acceptance evidence |
|---|---|
| Import contract | Positive fixtures: 4 Reading parts, 2 Writing tasks and 7 Speaking units (1,2,3–4,5,6,7,8), total 13 units/14 task formats. ZIP + JSON file + paste tested with appropriate assets. Strict rejection of wrong fields, counts, duplicate JSON keys, ordering, keys/indices, blank mismatches, wrong module, invalid identifiers, legacy prototypes and unsupported schema version. |
| Unsafe asset/input handling | Reject missing/corrupt/oversized images, mismatching extensions, traversal, ambiguous paths, duplicate ZIP entries, nested archives and unreferenced files. Invalid packages leave no visible partial data. Repeat valid import does not overwrite history; same ID/different content conflict. |
| Reading | Each part renders correct layout/groups/options; E mapping correct; sample grade matches known fixture, including unanswered. Keys and explanations withheld until submission; raw result and original selections survive restart. |
| Writing | Both task formats render; draft survives refresh/restart as interrupted/recoverable; counter consistent, choice and exact content preserved; empty/short/long expiry submits without forced correction; basic spell-check documented and confirmed where browser allows. |
| Speaking ordinary | Actual and simulated timers distinguish prep/record, original audio nonempty and playable, correct per-task duration, permission denial prevents prompt reveal, repeated attempts get separate names/IDs. |
| Speaking 3–4 | Same asset, no Task 4 preview; Task 3 saved before moving to Task 4; two separate recordings and shared session. Failed first save blocks advance; failed second preserves first. Manual practice plus timed sequence tested. |
| Speaking 5 | A/B only initially; both branches and no-choice timeout tested; chosen option and C only after lock; C hidden in pre-stage payload; three actual image references, one recording, selected ID and stage timings preserved. |
| Speaking 6/8 | Both either/or instructions visible for Task 6; no mandatory selection. Task 8 image and prompt displayed correctly with load gating. |
| Attempt integrity | Duplicate submit and save retry do not duplicate/overwrite. Browser closure/reload/interruption does not create false completion. Failed persistence reports failure and retains recoverable data where possible. Restart retains completed records. |
| Evaluation transfer | Copy includes necessary context only; paired scene included once; Task 5 rejected option omitted routinely; Finder reveals correct original asset, not a duplicate download. Compact ZIP export never includes unrelated history. |
| Daily launch | Production build starts locally, app reachable, data dir external; existing instance reused; port conflict handled safely. README has exact reproducible commands, versions and manual Mac steps. |

For automated testing use realistic synthetic fixtures with all required fields and actual small valid image files; mark fixture source as synthetic and do not present as CELPIP-level assessment. Any test-only override must default off for real sessions. Record commands, results and manual/browser checks separately. Avoid claiming "100% exam ready" or official equivalent scoring. If time-limited, report incomplete acceptance criteria rather than quietly removing requirements; learner can prioritize a usable core before optional polish.

## 10. Implementation tracker and decision control

Initial status: repository created/opened by learner; code, runtime, browser recording and importer have not been independently inspected or tested by this planning conversation. Codex should inspect repository at phase 1. Earlier mock packages are prototypes only. User's real exam practice data is not a test fixture.

| Phase | State | Evidence / next step |
|---|---|---|
| 0. Project initialization (Task 01) | COMPLETE | Both builds, backend context test, frontend typecheck/lint, HTTP readiness and two consecutive restarts passed; unfamiliar listener refused |
| 1. Backend | COMPLETE | Task 03 backend acceptance passed: 166 tests, packaged build, scoped media/export APIs and local script checks. Browser capture/countdowns/playback remain Phase 3 integration checks |
| 2. Frontend | IN PROGRESS | Tasks 04–05 COMPLETE and approved. Task 06 Writing COMPLETE and visually approved. Reading, Speaking and history interfaces remain pending |
| 3. Integration/test | NOT STARTED | Await frontend implementation |
| 4. Polish/launch | NOT STARTED | Await integration gate |

Update the relevant row and append a short dated change/test note when a phase actually finishes. If partially done use IN PROGRESS or BLOCKED, and identify remaining acceptance items. Preserve prior records. Keep user-facing reports concise. Do not confuse engineering completion with CELPIP learner progress.

2026-11-16 — Task 01 complete: Java 21.0.12, system Maven 3.9.16 (wrapper 3.10.1), Node 26.7.0 and npm 11.19.0 verified. Pinned Spring Boot 4.1.2, React 19.3.1, TypeScript 7.0.2 and Vite 8.3.8. Both dedicated ports were initially unused (`lsof`); both servers bind only to 127.0.0.1. `./mvnw -B -ntp verify`, frontend build/typecheck/lint and shell syntax check passed. Two consecutive script executions replaced PIDs 59574/59578 with 59875/59883; old processes exited and exactly one listener remained per port. Both static probes returned HTTP 200 and Chrome launch succeeded. A synthetic unfamiliar listener was refused and remained alive. Git ignore checks passed for build output, dependencies, logs, database files and recordings. Initializr emitted an invalid `.RELEASE` suffix; corrected to published Maven coordinate 4.1.2 before successful builds. No business logic, entities, application APIs or examination UI added. Task 01 brings development restart setup forward by explicit instruction; full backend/frontend feature phases and Phase 4 launch acceptance remain unstarted.

2026-11-16 — Task 02 implementation: external storage initialization/ownership lock, file H2, Flyway V1 migrations and Hibernate validation; package/exercise persistence, strict Appendix A validation, bounded ZIP/JSON/paste imports, image validation and content/image hashes, duplicate reuse/conflicts, transactional compensation and startup reconciliation. Added neutral health/config/import/ready DTO APIs with no-store and local host/origin checks. Canonical JSON is authoritative in H2; images use internal UUID directories, with questions/recordings/exports directories reserved. Reading passage-only duplicates with changed questions/keys are rejected rather than silently replaced; exact full duplicates return the original unit. Added 13 synthetic units/14 formats and documented multipart mapping, responses, limits, fingerprint rules and recovery in `backend/API.md`. Flyway is required for migration; Commons Compress is required for ZIP symlink/raw-path inspection. No Appendix A, reserved-port, restart-script or frontend changes. Verification: `./backend/mvnw -f backend/pom.xml -B -ntp verify` passed with 130 tests, zero failures/errors/skips, and an executable JAR. Every unit passed ZIP and JSON-with-mapped-assets imports; HTTP multipart ZIP/file/paste, neutral DTO/no-store checks, invalid contracts, duplicate IDs/content/prompts, asset safety/limits, transaction and filesystem failure compensation, and real file-H2 close/reopen persistence passed. Startup recovery uses a staging install journal: uncommitted journaled files are removed; committed files survive; unrecognized unjournaled images are preserved and block startup. Appendix A was compared against Task 01 and is unchanged. Task 01 commit `b70fdecab15457f0d5f1a424d322a995879c1c51` was pushed and verified against origin/main before Task 02 began. Task 02 is ready for its separately authorized commit/push; report the verified hash in the task handoff. No real preparation data was used in tests.

2026-11-16 — Task 03 complete: Flyway V2 adds persistent sessions and separate attempts; draft saving, strict stage/association checks, immutable/idempotent submissions, Reading raw grading, exact Writing responses/Unicode whitespace word counts, and common Speaking execution including paired 3/4 and Task 5 selection are implemented. Restart marks outstanding work interrupted while preserving completed recordings and drafts. Media saves use bounded WebM/Opus framing checks, original-byte files, journaled compensation and integrity checks; failures retain the current stage for browser-Blob retry. History/review, scoped image/audio retrieval, range playback, evaluation text, relevant-only ZIPs and managed Finder reveal are documented in `backend/API.md`. No native codec decoding, automatic speech evaluation or browser recording was added. WebM/Opus is the explicit supported capture profile; the frontend must feature-detect it before Start. No dependencies were added, Appendix A and reserved ports are unchanged, and no React features were implemented.

Task 03 verification: `./backend/mvnw -f backend/pom.xml -B -ntp verify` passed **166 tests**, zero failures/errors/skips, including all 130 Task 02 regressions. New coverage includes all Reading/Writing formats, all Speaking tasks in all three modes, Task 5 branch/fallback visibility, pair progression, duplicate/conflicting submissions, filesystem and transaction failures, restart interruption/draft/audio recovery, immutability, exports, managed-path/symlink refusal and actual HTTP audio range responses. Tests used temporary external data only. Native Chrome microphone permissions/capture, real MediaRecorder compatibility, audible cues, browser playback/visibility timing, spell-check and UI behavior remain explicitly pending frontend/integration checks; container framing validation does not assess speech quality.

Task 03 local launch evidence: shared ownership/port verification extracted to `scripts/local-processes.sh`, reused by restart and new stop scripts. Running original owned servers stopped gracefully; already-stopped repeated stop succeeded. Two temporary-storage restarts replaced PIDs 71906/71921 with 72274/72280; both servers became ready and health returned UP. A real synthetic evaluation export returned `revealed:true` from the Finder endpoint. Subsequent repeated stops released both ports and preserved data/logs. An unrelated synthetic listener on 47322 was refused and remained responsive. `bash -n` and `git diff --check` passed. Start: `./scripts/restart-local.sh`; stop: `./scripts/stop-local.sh`. No Shortcuts/bookmark setup was performed. Task 03 changes are ready for the separately authorized commit/push; the handoff records the verified remote hash.

2026-11-16 — Task 04: implemented only the React shell/homepage using the supplied palette, system font, 1120px content width, target scores, three module cards and two secondary action cards. Added consistent inline outline icons, responsive layouts, hover/focus/reduced-motion styles, real `/api/health` connection status polling, and five hash-based placeholder routes with Back navigation. No dependencies or backend changes. Frontend typecheck, lint and production build passed; `scripts/restart-local.sh` completed, opened Chrome and left both loopback servers running (backend PID 73505, frontend PID 73514). The frontend `/api/health` proxy returned UP with storage/database ready. Automated browser visual inspection was unavailable (Chrome browser connector unavailable; native computer-use startup failed); user visual review is pending. Per explicit Task 04 instruction, no commit or push until user approval, overriding the default completed-task commit workflow for this task.

2026-11-16 — Task 04 final styling revision: applied the approved Dark Navy palette through centralized CSS variables; white bordered cards, soft accent icon backgrounds, navy Practise buttons and hover states. The header logo and favicon share `frontend/public/maple-leaf.svg`; browser title remains CELPIP Practice. Removed the connection indicator and its health polling entirely. Existing layout, placeholder navigation, responsive breakpoints, focus and reduced-motion behavior are preserved. Typecheck, lint and production build passed; both existing servers remain running and Chrome was opened for appearance approval. No backend changes, commits or pushes. Visual approval remains pending.

Task 04 visual follow-up: lightened primary navy to `#243B60` and hover to `#304D78`, including the shared maple leaf logo/favicon; other palette and layout choices remain unchanged. Awaiting appearance approval; no commit/push.

Task 04 visual follow-up: removed the heading focus outline that appeared around “Choose your practice”. Heading focus management remains for navigation/screen readers; clickable controls retain visible keyboard focus. No commit/push.

Contract change control: Appendix A captures the exact canonical generation rules as supplied; if a later implementation uncovers an inconsistency, raise it with ChatGPT before making a breaking change. Changes to import shape, timing defaults, supported units or assessment rules require aligned changes to both the coaching Project Sources generation guide and repository specification, plus regression tests. The current Practice Material Registry already exists (ID `1LBhOrJYWFf8g8zEOYZwp76zuhCDwDU_Vmau_zAz8xDE`); the older creation-status language in the source guide is superseded by the live Project Instructions. The application does not read or write it. The guide and live registry use slightly different lifecycle labels; those are coaching-side records, not the local attempt-status enum. Do not mix them. The two-file versus three-file language in the older coaching guide is likewise superseded by the latest Project Instructions. No Drive integration needed in this repository.

Engineering defaults selected in this document without explicit user choice (Java 21, TypeScript/Vite, exact local ports/root, validation thresholds, route names, deterministic fallback A, bounded status implementation and shortcut mechanism) are sensible defaults to implement/verify, not additional product features or officially specified CELPIP behavior. Adjust only for a concrete compatibility or usability reason and document deviations. The user explicitly wants functional simplicity and a rapid build. Do not add unrelated future requirements.

---

## Appendix A — Canonical generation contract (verbatim source excerpt)

The following is reproduced from `PRACTICE_GENERATION_INSTRUCTIONS.md` v1.0, original sections 4–8. Original section numbering and wording are retained for compatibility. This excerpt includes the task timing reference, precise input shapes, and material quality constraints. Generator-only instructions (search, image generation, answer audit) are not functionality to implement in the app. Earlier sample schemas are superseded. The app must conform to this contract exactly; use the engineering requirements above for persistence and behavior details.

## 4. Official format reference and application timing defaults

The values below were checked against official 2026 materials at authoring. They are maintained centrally by the application. **Do not include timing fields in exercise JSON.** Reading counts and per-part time estimates are the agreed standard simulator shape, not a guarantee about every live exam. Official test totals may include other time or unscored material; do not replace the module timing with the sum of these practice defaults. Writing's word count is a guidance range, not a hard submission limit.

| Unit | Questions / responses | Selection (s) | Prep (s) | Answer time (s) |
|---|---:|---:|---:|---:|
| Reading 1: Reading Correspondence | 6 + 5 | — | — | 660 approx. |
| Reading 2: Reading to Apply a Diagram | 5 + 3 | — | — | 540 approx. |
| Reading 3: Reading for Information | 9 | — | — | 600 approx. |
| Reading 4: Reading for Viewpoints | 5 + 5 | — | — | 780 approx. |
| Writing 1: Writing an Email | 1 | — | within task | 1620 |
| Writing 2: Responding to Survey Questions | 1 | — | within task | 1560 |
| Speaking 1: Giving Advice | 1 | — | 30 | 90 |
| Speaking 2: Talking about a Personal Experience | 1 | — | 30 | 60 |
| Speaking 3: Describing a Scene | 1 | — | 30 | 60 |
| Speaking 4: Making Predictions | 1 | — | 30 | 60 |
| Speaking 5: Comparing and Persuading | 1 | 60 | 60 | 60 |
| Speaking 6: Dealing with a Difficult Situation | 1 | — | 60 | 60 |
| Speaking 7: Expressing Opinions | 1 | — | 30 | 90 |
| Speaking 8: Describing an Unusual Situation | 1 | — | 30 | 60 |

Main official format lists Reading 43–56 minutes, Writing 53 minutes and Speaking 15 minutes. The Reading study pack also describes a broader overall duration; the per-part values above are practice defaults. Writing Task 1 and 2 expect approximately 150–200 words each; planning and proofreading take place within the allocated task time. Basic spelling assistance and a word counter are available in the official test; use basic spell-check in the simulator but do not provide grammar correction, autocorrect, AI or predictive text. Treat this as the researched correction to the earlier no-spell-check proposal.

Practice mode is untimed but records elapsed times; user controls recording start/stop. Timed and Exam modes use the application defaults with automatic transitions/submission at expiration; exam mode disallows retries within the attempt. The user can repeat later as a separate attempt. These are local simulator conventions, not a claim of an exact reproduction of every test-centre control. Official timing updates require a coordinated simulator configuration review, not ad hoc package fields or an incompatible JSON change.

## 5. Canonical import contract (schemaVersion 1)

This section is normative for question generation and implementation. Earlier conversation examples and prototype ZIPs are superseded. They included duplicated `module` fields, timing objects, multiple unrelated exercises and SVGs which must not appear in new v1 imports. The field specifications below introduce the explicit shapes needed to remove ambiguity in the previously agreed contract; no arbitrary extra fields. If an example uses `...` or is labelled a fragment, it is structural illustration only, not a deliverable exercise.

### 5.1 Packaging

Preferred: a downloadable ZIP containing one UTF-8 `exercises.json` file at the root and, only if necessary, local image files inside `images/`.

```text
CELPIP-S-T03-T04-<unique suffix>.zip
  exercises.json
  images/scene.jpg
```

Text-only packages may be supplied as a downloadable JSON file or as a ZIP containing just `exercises.json`. Copy/paste JSON is supported when the learner intentionally wants it, but it risks revealing prompts and Reading answers. Prefer ZIP (or file import without opening its contents) for fresh practice. If an exercise references images, JSON pasted alone is incomplete; it must be accompanied by the indicated assets using the application's import mechanism. ZIP is the normal path for image exercises. Do not use remote image URLs, data URLs, base64 image payloads or inline executable markup. No recordings, sample answers, source PDFs or unrelated files inside the exercise ZIP.

Use actual JPG/JPEG or PNG images, with extension and file contents matching. Use plain relative forward-slash paths under `images/`, safe filenames and no path traversal, absolute paths, symbolic links, duplicate entries, nested archives or unreferenced files. An optional ZIP directory entry is harmless. Application limits should reject excessive sizes, decompression ratios and image dimensions; exact thresholds should be set in the technical implementation, not generated per exercise. Respect legitimate source attribution outside the learner-facing prompt.

### 5.2 Common envelope

```json
{
  "schemaVersion": 1,
  "packageId": "S-T01-20261116-a7c239f2",
  "module": "SPEAKING",
  "exercises": [
    {
      "exerciseId": "S-T01-20261116-a7c239f2",
      "taskNumber": 1,
      "source": {"type": "AI_GENERATED", "url": null},
      "content": {"prompt": "..."}
    }
  ]
}
```

| Field | Exact rule |
|---|---|
| `schemaVersion` | Required integer, exactly `1`. It identifies this canonical contract, not the guide's editorial version. |
| `packageId` | Required unique nonempty string; stable across re-delivery of the same artifact. Suggested module/part/date plus UUID or random hex suffix; use a tool-generated suffix when possible. IDs contain only ASCII letters, digits, `_` and `-`; no spaces or paths. |
| `module` | Required string, one of `READING`, `WRITING`, `SPEAKING`. Root only. |
| `exercises` | Required array: exactly one exercise except Speaking 3–4, which contains exactly two ordered items, 3 then 4. Never mix modules. |
| `exerciseId` | Required globally unique stable ID per exercise. A single-unit package may use its package ID as the exercise ID; a paired package must give each exercise a distinct ID. |
| `taskNumber` | Required integer; 1–4 Reading, 1–2 Writing, 1–8 Speaking. Reading uses this field even though CELPIP calls them parts. |
| `source` | Required object with exactly `type`, `url`. `type` is `ONLINE` (original source exercise reused where permitted; verified HTTPS URL required), or `AI_GENERATED` (original exercise; `url` must be JSON null). The registry records official vs third-party and exact locator, plus any separate image/factual sources. Do not label an adaptation as official or pretend a generated exercise came from a web source. |
| `content` | Required object matching the task-specific definitions below. |
| `answerKey` | Required only on Reading exercises, sibling of `content`. Forbidden on Writing and Speaking. |

All other keys are unsupported in v1 unless explicitly specified below. All required strings must be nonblank unless otherwise specified. JSON keys and enum values are case-sensitive; no comments, trailing commas, duplicate object keys, Markdown fences inside the actual file, unexpanded placeholders or lorem ipsum. Text is plain UTF-8 with `\n` for paragraph/line breaks. No HTML, JavaScript, embedded style instructions or UI-specific fields. Source URLs are metadata and not used to load exam assets. A new incompatible format requires an approved schema version change on both sides.

## 6. Reading contract — all four parts

### 6.1 Shared content and question model

All four parts use these exact content fields:

```json
{
  "instructions": "...",
  "passage": [{"type": "TEXT", "text": "..."}],
  "questionGroups": [
    {
      "type": "MULTIPLE_CHOICE",
      "questions": [
        {"id": "Q01", "prompt": "...", "options": ["...", "...", "...", "..."]}
      ]
    },
    {
      "type": "INLINE_BLANK",
      "text": "... {{Q07}} ...",
      "questions": [
        {"id": "Q07", "options": ["...", "...", "...", "..."]}
      ]
    }
  ]
}
```

The above is a partial structural fragment; the actual package must contain every question required by its part. Use `Q01`, `Q02`, ... in display order, unique within the exercise. Each group has `type`, `questions`, plus `text` only for `INLINE_BLANK`. Optional titles/extra metadata are not part of v1. Overall and group-specific directions can be conveyed in `instructions`, without solutions or hints. The question groups and passage appear on the same examination screen, with independent scrolling where useful; all questions remain editable until submission. Do not switch to another unit in the middle of a passage.

Passage block types (ordered array; avoid unnecessary fragmentation):

| type | Required additional fields | Use |
|---|---|---|
| `TEXT` | `text` string | Email, article, realistic headers and paragraphs |
| `PARAGRAPH` | `label` string, `text` string | Labelled paragraphs A–D in Part 3 |
| `IMAGE` | `image` local path | Actual visual diagram or brochure |
| `TABLE` | `headers` array of nonblank strings; `rows` array of arrays of strings | Structured flyer/schedule with at least one row; uniform column count matching headers. Empty cell strings are allowed if meaningful. Put surrounding conditions or captions in adjacent TEXT blocks. |

Passage blocks must contain the original reading evidence, but must not contain answer-key annotations, highlighted solutions, extra hints, or unrelated material. Preserve original line breaks, labels, units and qualifications. Part 2 can combine textual and tabular blocks to render a genuine visual resource rather than require image generation for dense numbers or schedules. If an existing diagram contains meaningful graphical relationships that cannot be represented with a table, use its permitted actual image. Ensure visual text is legible and accurate.

Question types:

- `MULTIPLE_CHOICE`: each question has `id`, `prompt`, `options` (exactly 4 distinct nonblank strings).
- `INLINE_BLANK`: group `text` contains tokens `{{Qxx}}` exactly once for each question in that group and no other blank syntax. Each question has `id`, `options` (exactly 4). No `prompt` on these questions; the enclosing text is the prompt. Order of tokens must match question order. All blanks are dropdown selections, not free-text fields. Preserve sentence punctuation and spacing around blanks. No solved text in the display.
- `PARAGRAPH_MATCH`: each question has `id`, `prompt` (statement), `options` exactly `["A", "B", "C", "D", "E"]` in that order. Directions explain E means the relevant information is not stated. A–D correspond to the exact passage labels. Options can repeat across different questions; no one-to-one matching constraint.

### 6.2 Reading answer key

The exercise has a separate key object. Example for two questions only:

```json
{
  "Q01": {"correctOption": 0, "explanation": "..."},
  "Q07": {"correctOption": 2, "explanation": "..."}
}
```

Every question ID must occur exactly once in `answerKey`; no extra keys. `correctOption` is a zero-based integer index into the question's options array, not a letter, not display numbering, not the answer text. Range: 0–3 for ordinary groups; 0–4 for paragraph matching (4 means E). `explanation` is a concise evidence-based nonblank string. Explain why the answer follows from a specific passage/diagram detail and, especially for close alternatives, why a competing answer fails. For E, explain the absence of the required information rather than quoting an invented detail. No source claims beyond the passage are needed for comprehension answers.

Never send the answer key from the backend to the client until the attempt is submitted. After submission, score one point per correct selection, zero for incorrect/unanswered, display total correct/total questions and the explanations, and preserve the actual responses. Do not substitute a per-part raw score for an official CELPIP level. Only use official approximate full-module conversion guidance with limitations when appropriate. A flawed question must be excluded or corrected transparently via an audited change; do not silently alter a learner's past result. Avoid reshuffling options in v1; any future shuffling must carry answer IDs/index mapping correctly.

### 6.3 Per-part validation matrix (exact v1 simulator requirements)

| Part | Required passage | Ordered groups | IDs |
|---|---|---|---|
| 1 | Everyday email, one or more TEXT blocks | MULTIPLE_CHOICE 6, INLINE_BLANK 5 (reply email) | Q01–Q06; Q07–Q11 |
| 2 | Visual diagram, at least one IMAGE or TABLE block; optional TEXT blocks | INLINE_BLANK 5 (short related email), MULTIPLE_CHOICE 3 | Q01–Q05; Q06–Q08 |
| 3 | Exactly four PARAGRAPH blocks, labels A, B, C, D in order | PARAGRAPH_MATCH 9 | Q01–Q09 |
| 4 | Article with two or more views, one or more TEXT blocks | MULTIPLE_CHOICE 5, INLINE_BLANK 5 (reader comment) | Q01–Q05; Q06–Q10 |

No other block combinations or group shapes in v1. Reject incomplete externally sourced units for simulator use; if a reputable source has a legitimate variation, use its own interface or agree an explicit new contract rather than silently deleting or fabricating questions to satisfy counts. Reading Part 3's five choices are the specific exception to the four-choice format.

### 6.4 Part-specific quality rules

**Part 1 — correspondence:** Complete realistic email with writer, recipient, purpose and enough connected details to support eleven questions. Reply email must fit the original relationship, dates, plans and conditions. Test gist, detail, tone and reasonable inference. Every completed sentence should make sense in context; grammar alone should not make an answer trivially obvious unless legitimately appropriate.

**Part 2 — diagram:** Build a useful realistic schedule, flyer, brochure or guide. Verify all times, dates, prices, eligibility, exceptions, totals and calculations, and the relationship between the email and the visual. Ensure each answer is supported by the relevant row/column/text/image detail. Do not cram microscopic text into generated imagery; prefer structured TABLE/TEXT for data. If calculations are needed, verify using a tool where practical. Distinguish information absent from information contradicted.

**Part 3 — information:** Four coherent, factual, reasonably substantial paragraphs with distinct content and some natural overlap. Nine statements must each be uniquely supported by one paragraph or not stated anywhere. Independently test each statement against every paragraph; distinguish a merely similar topic from the exact proposition. Do not make E answers by relying on outside knowledge. If multiple paragraphs fully support the same statement, revise it or reject the unit. No fixed quota of each letter, but avoid obviously patterned keys. Check any factual claims used in original passages with reliable sources, and record those sources separately from the exercise's origin.

**Part 4 — viewpoints:** Article on an accessible issue with at least two clearly distinguishable opinions, reasons and supporting details. Test whose view is expressed, attitudes, conditions, paraphrase and inference. The reader comment is a realistic response to the article, not a new unrelated passage. Each blank must fit both grammar and meaning. A correct answer cannot depend on which opinion the agent personally agrees with.

### 6.5 Mandatory independent answer audit

For every Reading unit, complete these steps BEFORE delivery, even when the source publishes an answer key:

1. Examine the entire passage/visual and all questions. Solve independently of the supplied/proposed answer key where feasible; avoid merely rereading the proposed correct answer and rationalizing it. If the key was visible during source retrieval, be clear that this was a separate verification pass, not fully blind independent review.
2. Establish direct textual/visual evidence or a justified inference for the selected answer. For each other option, identify why it is contradicted, incomplete, unsupported, changes a condition, misattributes a statement or otherwise fails. Check negation, quantifiers, time limits and qualifying words. Reject ties, vague prompts, missing details and defensible alternative answers.
3. Compare the independent choices with the proposed or published key; resolve every disagreement from the material, not source prestige or confidence. If a conflict remains, discard or transparently repair the unit. For original material, design distractors after establishing clear evidence, then re-solve the finished version. For externally sourced material, never misrepresent an altered question as official.
4. Insert the chosen options into inline blanks and read the fully completed email/comment for coherence; verify that other options do not also fit. For paragraph matching verify every A–D candidate against every statement.
5. Check that displayed option order matches each zero-based answer index after every edit. Check all question counts, unique IDs, blank tokens and keys programmatically if tools permit. Validate the final serialized file as well as its conceptual content.
6. Record a concise validation outcome in the registry. A second reasoning pass reduces errors; it is not a guarantee of correctness. Never claim separate independent agents reviewed the exercise unless that actually occurred. If the learner challenges a key, re-evaluate from the original evidence; admit corrections, preserve the original attempt and fix future use.

Difficulty should reflect natural increases across Parts 1–4, varied sentence structures, useful paraphrase and plausible distractors, not obscure specialist facts or trick wording. Do not make all questions simple keyword matches. Aim for a complete exam-like reading load, without inventing official word-count thresholds for passages.

## 7. Writing contract — both tasks

Use the common envelope with `module: "WRITING"`, one exercise, and the corresponding content object. No answer key, sample answer, suggested arguments, images or separate timing fields.

### Task 1 — Writing an Email

```json
{
  "scenario": "...",
  "instructions": "...",
  "requirements": ["...", "...", "..."]
}
```

`scenario` is the entire factual situation. `instructions` states the recipient, purpose and approximate 150–200 word target. `requirements` contains all ordered bullet instructions from the question, generally three, with four accepted when appropriate. Do not repeat those bullets inside `instructions`. For permitted existing materials preserve all requirements; do not omit a requirement to match a count. Reject a scenario that has no clear recipient, conflicting instructions or depends on specialist knowledge. Vary audience, tone and communication objective, not merely the names. The app displays all elements and the text editor together.

### Task 2 — Responding to Survey Questions

```json
{
  "title": "...",
  "scenario": "...",
  "instructions": "...",
  "options": [
    {"id": "A", "text": "..."},
    {"id": "B", "text": "..."}
  ]
}
```

`title` is a neutral topic heading; `scenario` identifies the survey context and intended audience; `instructions` asks the learner to choose and support one option in approximately 150–200 words. Options have exactly `id` and `text`, in A/B order; two distinct realistic alternatives, either reasonably defensible. No predetermined correct choice, no arguments supplied. Save the selected option with the response. In exam-like modes a missing selection or short/long response should not block time-expiry submission; retain what was actually submitted and flag omissions for evaluation rather than repair them.

Writing application: one shared plain text editor, live word counter, draft saving, basic spell-check only (disable predictive suggestions/grammar/autocorrection as far as browser/OS allows; warn if unavailable), timers and final submission. Do not enforce the 150–200 word target as a hard cap. Save the original response exactly as entered, word count using a documented consistent method, selected option if applicable, elapsed time, mode and attempt ID. Copy for ChatGPT exports only the full question, user's response, selection if any and relevant timings/conditions. Follow the coaching instructions for Level 9-oriented feedback.

## 8. Speaking contract — all eight tasks

Use the common envelope with `module: "SPEAKING"`. One recording per task, separate attempts on repeats. No answer keys, transcript, sample response, suggested vocabulary, timing fields or recordings in imports. All tasks require intelligible questions and ordinary English, not specialized subject knowledge. The actual question must remain hidden before the learner begins. Keep all task-specific content visible during preparation and speaking, subject to Task 5 stage visibility.

### Task 1 — Giving Advice

```json
{"prompt": "..."}
```

A realistic situation, clear intended recipient and a request for useful advice. Provide enough scope for advice supported by reasons/examples within 90 seconds. Do not list suggested solutions in the prompt. One text-only exercise.

### Task 2 — Talking about a Personal Experience

```json
{"prompt": "..."}
```

Invite a past experience or story with enough scope for a 60-second account. Include any original supporting directions. Use accessible experiences and avoid excessively specific assumptions about the learner's life. In evaluation, a realistic invented experience is acceptable; no requirement to reveal private experiences. One text-only exercise.

### Tasks 3 and 4 — Describing a Scene / Making Predictions (mandatory pair)

```json
{
  "schemaVersion": 1,
  "packageId": "S-T03-T04-20261116-a7c239f2",
  "module": "SPEAKING",
  "exercises": [
    {
      "exerciseId": "S-T03-20261116-a7c239f2",
      "taskNumber": 3,
      "source": {"type": "AI_GENERATED", "url": null},
      "content": {"prompt": "...", "image": "images/scene.jpg"}
    },
    {
      "exerciseId": "S-T04-20261116-a7c239f2",
      "taskNumber": 4,
      "source": {"type": "AI_GENERATED", "url": null},
      "content": {"prompt": "...", "image": "images/scene.jpg"}
    }
  ]
}
```

Exactly one shared physical image file. Both exercise IDs distinct, array order 3 then 4; both references must be identical. No standalone Task 3 or 4 in v1. Prioritize an unused online pair that genuinely shares the same scene, rather than unrelated source questions. If only one suitable reusable scene is available, an original matched pair can be developed around it, recording origin correctly. Task 3 requests present scene description; Task 4 requests plausible future predictions from the same visible activities. Neither prompt reveals the other's expected content. The visual should have several distinct, recognizable current actions and opportunities for reasonable next events. No action annotations or predictive hints.

At Start, show Task 3 image and prompt only; 30s preparation and 60s recording in timed modes. Save recording successfully, then automatically advance to Task 4 with the same image, a new prompt, new preparation and new recording. Untimed mode uses manual recording controls; after Task 3 is manually stopped and saved, automatically advance to Task 4, with unlimited preparation time before the learner manually starts its recording. Do not provide Task 3 feedback or preview Task 4 instructions between tasks in a fresh paired session. Preserve two independent attempt IDs with one shared session ID. If Task 3 saving fails, retain its audio and do not advance; if Task 4 fails, preserve Task 3. An interrupted or previously exposed pair cannot be represented as a fresh completed simulation. The evaluator receives the image once and both relevant recordings/prompts if reviewing the pair.

### Task 5 — Comparing and Persuading

```json
{
  "prompt": "...",
  "persuasionPrompt": "...",
  "options": [
    {"id": "A", "title": "...", "image": "images/option-a.jpg", "details": ["...", "..."]},
    {"id": "B", "title": "...", "image": "images/option-b.jpg", "details": ["...", "..."]},
    {"id": "C", "title": "...", "image": "images/option-c.jpg", "details": ["...", "..."]}
  ]
}
```

Exactly three option objects, in A/B/C order, each with the four specified fields. `details` is an ordered nonempty array of factual descriptive strings; supply enough comparable attributes for meaningful comparison (typically several). Use three distinct actual image files representing the options. Keep prices, times, dimensions, features and other text as structured details rather than baking them into generated pictures. Both initial choices must have credible advantages and trade-offs compared with C. Do not invent a correct choice. The `persuasionPrompt` must work regardless of whether the learner picks A or B, and assume that C is the other person's proposal. No dynamic template fields necessary.

Stage 1: display only `prompt`, A and B; 60 seconds for selection, no recording. C and the persuasion prompt stay hidden, including from the pre-start UI. Learner chooses one of A/B. At the end of the selection period, lock the chosen option; if no choice was made, automatically choose one of A/B and record that it was automatic (local fallback consistent with prior design; exact production-test default selection algorithm is not asserted). The simulator may advance on deliberate confirmation before timeout. Stage 2 displays `persuasionPrompt`, the retained original choice and C, with the other initial option removed. Begin 60 seconds preparation only when both images are ready; then record one 60-second response. Untimed mode allows unlimited selection/prep and manual recording. Save selected ID, manual/automatic selection flag and actual selection duration in addition to ordinary attempt fields. Evaluation needs only the chosen and competing options, both stage instructions, timings and recording, not the rejected initial option unless needed to investigate an issue.

### Task 6 — Dealing with a Difficult Situation

```json
{"prompt": "...", "alternatives": ["...", "..."]}
```

A genuine interpersonal conflict or difficult choice. `alternatives` contains exactly two complete independent instructions, displayed as EITHER/OR. Each identifies whom to address and what position/decision to communicate. They may address the same or different people/groups. Both alternatives should be reasonably defensible; do not substitute a normal advice or opinion question. No mandatory electronic selection, extra stage or image. Both alternatives remain visible, with one preparation period and one recording. Evaluation identifies the chosen alternative from the response or asks if unclear.

### Task 7 — Expressing Opinions

```json
{"prompt": "..."}
```

An accessible opinion question with plausible differing viewpoints and scope for supporting reasons in 90 seconds. Include complete source instructions. No separate electronic choice, prescribed number of arguments, preferred stance or sample answer. One text-only exercise.

### Task 8 — Describing an Unusual Situation

```json
{"prompt": "...", "image": "images/unusual.jpg"}
```

The complete prompt must identify an unusual object or situation, the recipient and communication purpose (often a simulated phone call). Choose a visual with distinctive, interpretable shapes, features or details that can be described using everyday words for a full response. It should not require knowledge of an obscure name or technical label. Unlike Task 3, focus on communicating distinctive features to a specific listener, not just listing activities. One actual image and one recording; no option selection. Do not provide the object name or descriptive vocabulary if that would defeat the purpose of the question.

### 8.1 Visual sourcing, generation and inspection

For Tasks 3–4, 5 and 8, think through the exercise and its required visual BEFORE selecting or generating the actual image. Prefer unused online images that fit the required format and can be reused lawfully. Use a source's own interface if images cannot be obtained or packaged appropriately. Do not claim the agent retrieved files based only on thumbnails/search snippets. If generating original visuals is necessary, work on a single practice unit, not an eight-task mock. Use the actual image-generation capability when available, not basic programmatic stick figures, SVG cartoons or mockups of the intended interface. Create professionally illustrated or clear realistic visuals with meaningful details; avoid nonsensical hands, confusing actions, cramped text and irrelevant decorative elements. For Task 5, generate/obtain three separately usable assets; a multi-panel composite is acceptable only if correctly separated and every resulting individual asset is inspected for consistency and completeness. Do not generate a composite preview for the learner.

Inspect the actual image files, not just the source description or generation prompt. Check that the picture matches the scenario, that each asset is correctly labelled in JSON and that all required details are legible at ordinary screen size. Do not put solutions or explanatory labels onto the image. Verify exact bytes, dimensions and decoder validity where tools permit. No invented image links, fabricated download paths, or claims that a file was saved unless verified.

**Spoiler limitation:** An image-generation tool may display its result in the chat immediately; a promise to show only the final ZIP cannot suppress that interface. Do not silently expose fresh test images. Prefer non-preview retrieval of reusable existing image files. If image generation would visibly spoil the exercise, tell the learner briefly before generating; proceed only if the learner accepts preview exposure or a separate permitted asset-creation workflow. Mark exposed material `SEEN`, and use for learning, not fresh assessment. An agent cannot guarantee that generated pictures can be secretly saved or hidden through a link. If suitable visual assets cannot be obtained, deliver a verified text-only task only with agreement or direct the user to a suitable external exercise. Never substitute inadequate assets and call them examination-ready.


Task 04 approved: primary navy #243B60; hover #304D78, shared by application branding and favicon. Task 05 imports/ready screen now authorized; Task 05 commit awaits separate approval.

Task 05: shared module-scoped upload/drop/paste interface with explicit image path mapping, loading lock, backend errors and duplicate reuse. Neutral ready card shows only module/task/count and mode selection; Start remains disabled and no session/content/image endpoints are called. Frontend typecheck/lint/build passed; 37 isolated ImportIntegrationTest cases passed covering ZIP/JSON/paste, invalid/duplicate/mismatched imports and neutral responses. Automated browser interaction/visual review remains pending. Task 04 commit 73ea54c25742fd02a5c985a24a9bab358807844f pushed and remote-verified before implementation. Task 05 is uncommitted pending user approval.

Task 05 review follow-up: user confirmed Reading and Speaking loading works. Renamed visible “Import exercise” actions/headings to “Load exercise”; moved Practice mode heading inside a padded radio-group panel to avoid border overlap. Start remains intentionally disabled pending examination UI. No commit/push.


### Task 06 — Writing interface (complete; visually approved)

Task 05 commit `905b3cdf12ef7b793437d63987efb2c98597068c` was already on local and remote main before Task 06; no duplicate commit was created. This supersedes earlier Task 05 pending-commit notes.

Writing alone now enables explicit Start from the neutral ready card. `POST /api/sessions` is called only on that click; no question/content fetch occurs beforehand. Both canonical Writing tasks share a 40/60 question/editor layout using the approved navy palette. Task 2 saves the A/B choice with the original text. Reading and Speaking execution remain disabled; homepage design and backend contracts are unchanged.

Reusable `usePracticeTimer` uses a monotonic start/deadline and backend session timing (1620/1560 seconds). Practice shows elapsed time; Timed/Exam expire automatically, including after delayed visibility updates. Detected suspension/clock discontinuity interrupts instead of extending a fresh attempt. Drafts debounce for 600 ms and serialize; submission drains prior writes, freezes the exact text/choice/duration for idempotent retries, and rejects further draft writes. Manual submission and abandonment require confirmation. Reloaded work is shown as interrupted recovery, with the latest same-tab sessionStorage text and durable server drafts retained; it never silently resumes as fresh practice. Browser close/reload interruption delivery is best effort, with recovery reconciliation on reload and the existing server restart recovery as fallback.

The shared counter counts nonempty Unicode-whitespace-delimited tokens (hyphenated words remain one token); 150–200 is guidance only. The result displays the saved original text, survey choice, backend word count and measured duration, with evaluation-context copying. No scores/corrections are generated. Basic spell-check is enabled; autocorrection/autocapitalization/autocomplete and known grammar-extension hints are disabled. Browser/OS extensions cannot be fully controlled.

Verification: frontend `npm run typecheck`, `npm run lint`, `npm test` (16 passing tests), `npm run test:integration` (1 passing isolated real-backend scenario covering both tasks in all three modes, plus empty expiry payloads/interruption), and production build. Tests cover canonical rendering, Unicode counts, absolute deadlines after skipped ticks, ordered drafts, failed drafts/submissions, frozen retries, manual/expiry duplicate prevention, exact response/choice persistence, evaluation context and neutral metadata. Integration launches a temporary backend on an ephemeral loopback port with a canonical temporary data directory; it never imports into real preparation storage. `scripts/restart-local.sh` also passed all 166 existing backend tests and restarted both dedicated servers.

Chrome was opened by the restart script. Automated browser interaction is blocked by the unavailable Chrome connector/native inspection timeout; actual visual layout, keyboard interactions, native spell-check/clipboard and end-to-end browser expiry/reload remain for manual inspection. Unit clock/queue checks and server-rendered component checks are not claimed as browser automation. No dependencies were added, and no Task 06 commit/push is permitted until the user approves the interface. Reading, Speaking, history and subsequent feature work have not begun.

Task 06 visual follow-up: added a 16px wrapping gap between confirmation buttons and result actions. Saved-response actions now place the white Return to homepage button first, followed by navy Copy for ChatGPT, including recovered completed responses. Awaiting visual approval; no commit/push.

Task 06 review follow-up: ready actions have a 16px gap. Start practice now opens a keyboard-accessible confirmation dialog; only Start test creates the Writing session and begins timing. Cancel leaves the exercise ready with no question retrieval or session creation.

Task 06 exit follow-up: added Exit without saving alongside Save draft and exit in the confirmation dialog. It cancels the pending debounce, stops queued draft writes, waits for any already-running write, and marks the attempt interrupted without a final draft save. The dialog explicitly states that earlier autosaved drafts remain stored; this does not delete historical or autosaved data.

Task 06 approved for commit, including spacing, result action order, Start confirmation and Exit without saving refinements. Browser automation limitations recorded above remain explicit.
