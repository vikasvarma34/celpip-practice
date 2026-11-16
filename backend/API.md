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
