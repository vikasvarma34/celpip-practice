# Backend foundation API (Task 02)

Base URL: `http://127.0.0.1:47321`; frontend uses `/api` via Vite on port 47322. Every `/api` response has `Cache-Control: no-store`. Import data never becomes a static web resource. All response objects are DTOs; no entity serialization.

## GET /api/health

200 when storage directories are writable and a database query succeeds; otherwise 503:

```json
{"status":"UP","storageReady":true,"databaseReady":true}
```

No local filesystem paths or content are returned. Startup logs contain the resolved data directory. Startup fails if migration, storage lock, recovery or stored-image integrity checks fail.

## GET /api/config

200 with `schemaVersion: 1`, `modules: ["READING","WRITING","SPEAKING"]`, `modes: ["PRACTICE","TIMED","EXAM"]`, `timings` and `importLimits`.

`timings` contains 14 objects with fields `module`, `taskNumber`, `selectionSeconds`, `preparationSeconds`, `answerSeconds`. Zero means no separate phase. Values are the Appendix A defaults, held centrally in `FoundationController`; practice mode will use manual progression without enforced limits. This endpoint advertises configuration, not implemented practice-session functionality.

`importLimits` defaults (bytes, except where named otherwise):

```json
{"compressedBytes":31457280,"extractedBytes":62914560,"jsonBytes":2097152,"imageBytes":15728640,"maxPixels":25000000,"maxEntries":16,"maxRatio":100}
```

Override with `celpip.import.compressed-bytes`, `extracted-bytes`, `json-bytes`, `image-bytes`, `max-pixels`, `max-entries`, `max-ratio`. Ratio checks apply after the first MiB per ZIP entry. HTTP multipart also has transport limits of 30 MiB per file and 65 MiB per request; adjust `spring.servlet.multipart.max-file-size`/`max-request-size` together with import limits when intentionally increasing them.

## POST /api/imports

Content type: `multipart/form-data`. Exactly one of:

- `file`: one `.zip` upload containing root `exercises.json` and only its referenced images; no separate assets with a ZIP.
- `file`: one `.json` upload, optionally with mapped assets.
- `json`: one UTF-8 **text form field** containing the canonical JSON (not a JSON file part), optionally with mapped assets.

For separate images, repeat `assets` file parts and `assetPaths` text fields in corresponding order. Each `assetPaths` value must exactly equal a referenced canonical `images/...png|jpg|jpeg` path. Browser filenames are deliberately ignored for mapping; no inferred names. Supply each shared image once. All references must be supplied, with no extras or duplicate paths. Optional `expectedModule` is one of the module names and must match the package. Unknown form fields and multiple `file`, `json` or `expectedModule` values are rejected. The canonical exercise JSON remains exactly Appendix A; this multipart transport introduces no exercise fields.

From the repository root:

```sh
curl -F 'file=@fixtures/writing-1.json' -F 'expectedModule=WRITING' http://127.0.0.1:47321/api/imports
curl -F 'json=<fixtures/speaking-8.json' -F 'assets=@fixtures/images/asset-0.png' -F 'assetPaths=images/asset-0.png' http://127.0.0.1:47321/api/imports
```

Use a separate `CELPIP_DATA_DIR` for these synthetic fixtures rather than learner storage.

201 for a newly committed unit, 200 for an exact existing unit:

```json
{"packageId":"SYNTHETIC-SPEAKING-3-4","module":"SPEAKING","schemaVersion":1,"taskNumbers":[3,4],"exerciseCount":2,"status":"IMPORTED"}
```

An exact duplicate returns the original stored package ID and `status: "EXISTING"`, even if re-delivered with different IDs. No alias records or replacement files are created. A package/exercise ID that identifies different content produces 409 before duplicate reuse. An already-stored Reading passage with a different question/key set produces 409 `PROMPT_DUPLICATE`; reuse of a constituent exercise/prompt in a different unit produces `EXERCISE_DUPLICATE`. This prevents silent revisions; any future correction workflow requires an explicit task.

## GET /api/packages/{id}

200 with precisely the same six receipt fields and `status: "READY"`. Missing ID: 404. It returns no prompt, passage, question, source URL, image reference, fingerprint, answer key or future task content. No list/content/asset endpoint is implemented in this task.

## Errors and local access

```json
{"code":"INVALID_PACKAGE","message":"Only schemaVersion 1 is supported.","fieldErrors":{"schemaVersion":"Only schemaVersion 1 is supported."}}
```

400 malformed JSON/request; 422 contract/archive/asset mismatch; 409 ID/content conflict; 413 limits; 404 missing package; 503 storage/database failure. Error text uses schema paths and fixed explanations, never imported field values, parser excerpts, answers, exception traces or private paths.

Host checks allow loopback hostnames only. Mutating browser requests must have an allowed local HTTP Origin on port 47321 or 47322; cross-site fetch metadata is refused. Requests without Origin remain available for local CLI clients. No wildcard CORS, authentication or security framework is added.

## Storage and transaction decisions

- `LocalStorage` creates database, questions, images, recordings, exports and staging under an absolute external root, default `~/CELPIP-Data`. A process-level file lock prevents simultaneous owners. Symlinked root components/managed directories and Git-checkout storage roots are rejected; resolve macOS `/tmp` to `/private/tmp` for manual test roots.
- Flyway `V1__exercise_imports.sql` creates two tables and maintains schema history/checksums. Hibernate only validates the schema. Future migrations must be additive/non-destructive. H2 is file-based, including integration tests in fresh temporary roots.
- `exercise_package.canonical_json` is the sole authoritative content representation, including source metadata and private Reading keys. It stores sorted-key, NFC/LF-normalized UTF-8 JSON. `imported_exercise` stores globally unique IDs, package association, order, task and hashes. The `questions` directory is reserved; there is no divergent JSON file copy.
- Managed image files live under `images/<internal UUID>/<relative image path without images/>`. Internal UUIDs avoid filesystem limitations from long external IDs. The manifest maps canonical references to SHA-256 byte hashes. No absolute file paths are imported or stored in canonical JSON.
- Full exercise hashes include module, task, content and Reading key; exclude IDs/source metadata; replace image references with actual byte hashes. Prompt hashes use Reading passage blocks only, and the full task content for Writing/Speaking. Package hashes cover ordered exercise hashes. Sorted keys, retained array order, NFC strings and LF newlines are used throughout. Exact image renames do not defeat duplicate detection; different bytes with the same name are distinct. These hashes do not detect semantic paraphrases.
- Imports are serialized within the single owning process. Bounded streams stage uploads; ZIP central-directory attributes expose symlinks and raw names expose path normalization. JPEG/PNG signatures, extensions, dimensions, complete decoding and PNG CRCs are checked. Source HTTPS URLs are syntax-checked metadata, never fetched.
- All validation and collision checks precede image installation. Files move from staging into a new UUID directory, then one database transaction saves package and exercise rows. Failed transactions remove installed images; failed file moves never commit rows. Commit-state checks avoid deleting files if a commit succeeded but its acknowledgement failed. Before installing files, a staging journal records the new UUID. On restart, only journal-identified uncommitted image directories are removed; committed images are retained and their hashes verified before readiness. Unrecognized image directories without a journal are preserved and block startup for inspection or backup restoration. Tests cover transaction and filesystem failures plus simulated crash leftovers. This is compensating recovery, not a cross-resource atomic transaction or a power-loss guarantee.

Content quality, source reuse rights, factual answer correctness, scenario coherence and visual suitability remain generator/reviewer responsibilities, as specified in Appendix A. The importer enforces structural, type, reference and safety constraints; it does not invent NLP or AI assessments.

Implementation references: [Commons Compress ZIP metadata and symlink attributes](https://commons.apache.org/proper/commons-compress/zip.html), [Jackson 3 migration/release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0).

# Practice execution API (Task 03)

All JSON request objects below have exact fields: unexpected/missing fields, duplicate keys and numeric coercion are rejected. Durations are nonnegative integer **milliseconds**, measured by the frontend's monotonic clock after assets are ready. No server wall-clock proctoring is introduced. Speaking TIMED/EXAM transitions require reported elapsed preparation/response at least the configured duration; PRACTICE uses manual progression. Reading/Writing may submit early and may submit empty at expiry. Client timers/microphone preflight are frontend responsibilities.

## POST /api/sessions

Request: `{"packageId":"…","mode":"PRACTICE"}` (also TIMED, EXAM). Every call creates a new session and first attempt; retrying Start creates a distinct attempt, so disable duplicate Start in the UI. A paired package starts Task 3 only; Task 4 has no attempt until Task 3 is saved.

Response (also `GET /api/sessions/{id}`):

```json
{
  "sessionId":"uuid","packageId":"id","mode":"PRACTICE","status":"IN_PROGRESS",
  "attemptId":"uuid","taskNumber":1,"stage":"PREPARATION",
  "timing":{"module":"SPEAKING","taskNumber":1,"selectionSeconds":0,"preparationSeconds":30,"answerSeconds":90},
  "draft":null,"content":{"prompt":"The active prompt"}
}
```

Stages: Reading/Writing `RESPONSE`; Speaking `SELECTION` (Task 5 only) → `PREPARATION` → `RECORDING` → `COMPLETED`. Other Speaking tasks start at `PREPARATION`. Status is `IN_PROGRESS`, `COMPLETED` or `INTERRUPTED`. Completed session retrieval omits content; explicitly open attempt review. Interrupted sessions retain their last stage and exposed content, with `interruptionReason`. Images in content are **opaque asset IDs**, replacing import-relative paths. Source metadata and Reading keys are excluded.

## POST /api/sessions/{id}/advance

```json
{"attemptId":"uuid","action":"LOCK_SELECTION","elapsedMs":60000,"choice":null,"timeout":true}
```

All five fields required. `LOCK_SELECTION` is valid only for Task 5 selection: choice A/B, or null at timed timeout (fallback A, `automaticChoice:true`). Explicit confirmation requires A/B and `timeout:false`; PRACTICE does not allow timeout. Before lock, only A/B and initial prompt are returned. After lock, only the chosen option and C are returned, with persuasion instructions. Selection cannot be changed.

`START_RECORDING` requires PREPARATION, `choice:null`, `timeout:false` and measured preparation `elapsedMs`. Return: updated session DTO. No explicit Task 3→4 advance exists: successful audio submission is its sole transition. Stale/repeated advance requests return 409; GET the session to reconcile. Stage calls cannot reference another session's attempt.

## PATCH /api/attempts/{id}/draft

Reading: `{"answers":{"Q01":0,"Q02":null}}`. Missing or null means unanswered. Unknown IDs, strings/floats or out-of-range indices are rejected.

Writing 1: `{"text":"exact original text"}`. Writing 2: `{"text":"exact original text","choice":"A"}` (A, B or null). Empty text is valid. Response: `{"attemptId":"uuid","saved":true}`. Only an active RESPONSE attempt accepts drafts; completed/interrupted attempts cannot be modified.

## POST /api/attempts/{id}/submit

Reading/Writing use `application/json`:

```json
{"response":{"answers":{"Q01":0}},"durationMs":660000}
```

`response` has the same module-specific shape as draft. Successful response is the review DTO described below. Submission persists exact input strings without trimming, newline normalization or spelling correction. Submission equality ignores JSON object-key order but preserves actual response values and durations; same submission returns the stored review, changed submission returns 409. Reading scores one per correct and zero per wrong/unanswered, with no official score conversion. Writing has no score.

Word count is the number of runs of non-whitespace under Unicode `White_Space` semantics (Java `Pattern.UNICODE_CHARACTER_CLASS` and `\S+`). Frontend equivalent: `text.match(/[^\p{White_Space}]+/gu)?.length ?? 0`. Hyphenated words and contractions count as one; punctuation-only tokens also count. Original text is unchanged. 150–200 is guidance, not a cap.

Speaking uses `multipart/form-data` with `audio` (one file) and `metadata` (text field containing `{"durationMs":60000}`). Supported profile: **audio-only WebM/Opus**, MIME `audio/webm` or `audio/webm;codecs=opus`, original bytes retained, no transcoding. Frontend must feature-detect this profile before revealing the prompt; other containers/codecs return 422 rather than guessing an extension. The configurable `celpip.audio.max-bytes` defaults to 50 MiB, with HTTP per-file ceiling 50 MiB and total multipart ceiling 65 MiB. Import-specific 30 MiB ZIP/15 MiB image limits remain unchanged.

Validation checks EBML framing/bounds, WebM doctype, one audio Opus track and OpusHead, complete nonempty unlaced audio blocks, MIME and limits. This is container/codec framing validation, not speech intelligibility assessment or a native Opus decoder. Chrome MediaRecorder capture, its output compatibility, actual playback and suspension behavior remain integration checks.

Audio response: `{"attemptId":"uuid","saved":true,"session":{…updated session DTO…}}`. Task 3 success creates Task 4 and returns its preparation content; Task 4 failure cannot erase Task 3. Repeating identical audio bytes/duration returns acknowledgement without creating another recording or attempt; conflicting bytes/duration return 409. On 503, retain the frontend Blob and retry the same attempt; no success/advance is returned before the DB transaction commits. Unsaved browser data cannot be recovered by the server after browser loss.

## POST /api/sessions/{id}/interrupt

Request: `{"reason":"ABANDONED"}`; also `BROWSER_RELOAD`, `DEVICE_ERROR`, `SUSPENDED`. Marks only outstanding work interrupted; completed attempts remain completed. Repeated interruption is safe. Returns session DTO. Startup marks all previously in-progress sessions/attempts interrupted with `APPLICATION_RESTART`, preserving drafts and earlier paired recordings. Interrupted sessions cannot resume as fresh exams; review/copy their drafts and deliberately start a new session for reattempts.

## GET /api/history and GET /api/attempts/{id}/review

History is a newest-session-first array. Each row has `sessionId`, `packageId`, `mode`, `status`, `createdAt`, and ordered `attempts`. Attempt summaries contain `attemptId`, `sessionId`, `exerciseId`, `module`, `taskNumber`, `attemptNumber`, `mode`, `status`, `stage`, `createdAt`, `durationMs`, `preparationMs`, `selectionMs`, `choice`, `automaticChoice`. Completed Reading summaries also contain `correct` and `total`. Unstarted imports are absent; paired attempts share one row. No prompts, answer keys or response text appear in history.

Review requires COMPLETED or INTERRUPTED. It contains the same attempt summary plus exposed `content` and saved `draft`. Completed attempts add `response` and `result`; audio attempts add `audioUrl`. Interrupted work has no results. Reading result: `{"correct":1,"total":11,"questions":[{"questionId":"Q01","correct":true,"selectedOption":0,"correctOption":0,"explanation":"…"},…]}`. Writing result: `{"wordCount":170,"outsideTarget":false,"missingChoice":false}`. Speaking has null response and empty result; choice/timings/audio reference are in the attempt DTO.

## Controlled files

- `GET /api/assets/{assetId}?sessionId={id}` accesses only the current accessible stage's images. Alternatively `?attemptId={id}` accesses explicit completed/interrupted review images. Supply exactly one scope. Future Task 4 or Task 5 C cannot be fetched early, and the rejected Task 5 option is inaccessible after lock.
- `GET /api/attempts/{id}/audio` serves only completed recordings as `audio/webm`; Spring Resource handling supports browser range requests. Missing/interrupted/unsubmitted recordings cannot be retrieved.
- No request accepts an arbitrary path. Managed file resolution verifies root containment, existence and every path component for symlinks. All API responses, including file responses, use no-store.

## Evaluation and exports

`GET /api/attempts/{id}/evaluation-context?paired=false` returns `{"text":"human-readable context","assets":[{"id":"…","url":"…","kind":"IMAGE|AUDIO"}]}`. Completed attempts only. `paired=true` requires both Tasks 3 and 4 completed in the same session, includes both recordings/prompts and the shared image once. Reading contains the full passage/diagram for evidence and wrong/unanswered items with their selected/correct choices and explanations. Writing includes full question, exact response, choice, count and timings. Speaking includes original relevant instructions and timings; Task 5 excludes the rejected option. No unrelated history, private paths or unrevealed content is included.

`POST /api/exports` request: `{"attemptId":"uuid","paired":false}`. Creates an explicit immutable evaluation ZIP, response `{"exportId":"uuid","downloadUrl":"/api/exports/uuid"}`. `GET /api/exports/{id}` downloads it. ZIP contains `evaluation.txt`, relevant original recordings and images only, with shared images deduplicated. Export is an evaluation artifact, not an exercise-import package.

`POST /api/reveal` request: `{"kind":"AUDIO","id":"attempt UUID","attemptId":null}`; use `kind:"EXPORT"` with export UUID or `kind:"IMAGE"` with asset ID and completed/interrupted review `attemptId`. Calls `/usr/bin/open -R` through a ProcessBuilder argument list after managed-path checks. Success: `{"revealed":true}`; unsupported OS, missing file or launch failure returns an error. No shell/path input is accepted.

## Persistence and recovery additions

Flyway V2 adds `practice_session` and `practice_attempt`; previous migrations/imports are unchanged. Imported canonical content is immutable, so attempts refer to the original exercise. Attempts preserve drafts, exact responses, historical grading results, UTC timestamps, sequential attempt numbers and original audio metadata. Mutations are serialized within the one owning application process; transactions create/complete attempts and pair advancement together.

Recording names include the system-local date, bounded exercise ID, attempt number and UUID, under an internal package directory. Media saves stage bytes and a journal before installation, then commit metadata. Failures compensate files; restart removes journaled uncommitted recordings and verifies committed audio hashes. Import recovery explicitly leaves media journals for media recovery. Unjournaled recordings are preserved, never automatically served. Exports are written to temporary ZIPs then moved atomically to random managed IDs. Back up the complete data root with the application stopped.

WebM framing references: [WebM container guidelines](https://www.webmproject.org/docs/container/) and [Matroska elements](https://www.matroska.org/technical/elements.html).

Speaking foundation (Task 08A): Tasks 1/2/6/7 evaluation text is concise original prompt/alternatives plus mode and human-readable preparation/speaking durations. Context response fields and separate audio assets are unchanged. No new draft or capture endpoint is introduced.


## Speaking audio conversion refinement (Task 08A)

The original WebM bytes, audio URL, hashes and completed attempt remain unchanged. Successful multipart audio submission queues a separate local FFmpeg conversion; its response still acknowledges only the original durable save. Derived mono AAC audio at 64 kbps is installed atomically as a sibling `.m4a` with the original filename stem. Conversion uses no audio filters, silence removal, enhancement or trimming. A single local worker and per-attempt status prevent concurrent/repeated conversion; existing nonempty sidecars are reused across restarts. Failed partial output is removed; original saving is never rolled back by conversion failure.

- `GET /api/attempts/{id}/audio-conversion`: completed audio attempts only. Returns `{status,message,audioUrl}`; status is QUEUED, CONVERTING, READY, FAILED or UNAVAILABLE. For a legacy recording without a sidecar/status, queues conversion automatically. READY includes `/api/attempts/{id}/audio?format=m4a`; otherwise audioUrl is null.
- `POST /api/attempts/{id}/audio-conversion`: retry a failed/unavailable conversion. Queued/running/ready requests are idempotent.
- `GET /api/attempts/{id}/audio?format=m4a`: controlled M4A resource (`audio/mp4`), 404 until ready. Omitted format or `format=webm` preserves original WebM behavior. Browser range playback remains supported.
- Existing `POST /api/reveal` with kind AUDIO prefers the M4A sidecar when present, otherwise the original WebM. Copy for ChatGPT output is unchanged.

FFmpeg is an external local executable, discovered on PATH and usual Homebrew paths, or configured with `celpip.ffmpeg` as an executable path. If missing, install using `brew install ffmpeg`, then retry conversion; no restart is required for normal discovery. Conversion has a 120-second timeout and fixed actionable errors. No external service, Java dependency or database migration is added. The AAC sidecar is a derived file; the original WebM remains authoritative.


Task 08B frontend support: Speaking 3/4 remain a mandatory pair under Appendix A; no standalone task-start or independent pairing mode was added. Task 3 audio acknowledgement already returns the newly created Task 4 attempt and prompt; the UI consumes it without creating another session. Task 4 reuses the same scoped image ID/bytes, with a new microphone/recorder and fresh backend-configured preparation/response timers. Original WebM and derived M4A handling is unchanged. Task 8 uses the same image/recording flow independently. Individual 3/4/8 evaluation text now includes readable task names, prompt, mode, durations and an attachment reminder. `paired=true` separates both contexts and identifies the shared image once; completed-only checks and existing assets/Finder scopes remain unchanged.

POST /api/clipboard: {attemptId,kind,paired,path}; kind AUDIO/IMAGE/ALL; boolean paired selects only a completed 3/4 pair, boolean path copies verified absolute paths as plain text, otherwise native NSPasteboard file URLs. Audio requires ready M4A; files resolved inside managed data root with symlink checks. Existing loopback/origin mutation restrictions apply. Failure is reported rather than claiming clipboard success. No arbitrary path input.

GET /api/speaking-testing returns {earlyFinish:boolean}. Temporary celpip.speaking.testing-early-finish=true permits shorter Speaking preparation/recording durations for local testing; false (code default) restores strict checks. Actual timings are preserved and shortened timed attempts are identified in evaluation text.

Clipboard ALL now copies native M4A/image URLs plus a separate plain-text metadata item and UTF-8 metadata TXT attachment. ALL_FILES copies the same files/TXT without the text pasteboard item for destination compatibility fallback. TXT content comes from the scoped completed evaluation context, stored at a server-generated content-hashed exports filename, never a caller path. Existing files are verified and reused. No WebM is substituted when M4A is missing; missing conversion fails before modifying the clipboard. Individual AUDIO/IMAGE and path flags remain supported. Browser/ChatGPT mixed-content paste is not claimed verified.


Task 08C frontend support: Task 5 SELECTION shows only selection instructions and A/B, with no microphone capture/permission request. Both images decode before the configured selection clock. LOCK_SELECTION retains the existing exact request; timeout=true requires the configured elapsed selection time and chooses A only when choice is null. Explicit confirmation may lock early. PREPARATION returns the selected option and C plus persuasion instructions; both images decode before the existing preparation/recording execution starts. No additional attempt/session/schema endpoint was introduced. Review already persists choice, automaticChoice, selectionMs, preparationMs and durationMs. Evaluation text now renders full human-readable Task 5 instructions and chosen/C details only; attachment scopes remain restricted accordingly. Import additionally enforces distinct byte hashes for its three images.

POST /api/clipboard optionally accepts assetId only with kind IMAGE and paired=false. The asset must belong to that completed attempt's visible managed image set. Without assetId, IMAGE copies all relevant images; ALL/ALL_FILES still copy relevant M4A/image(s) plus details TXT/plain-text fallback. Existing response/error contracts and loopback/origin restrictions remain.
