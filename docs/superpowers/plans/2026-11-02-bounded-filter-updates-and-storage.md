# Bounded Filter Updates and Reliable Local Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make user-selected filter downloads safe and efficient, enforce bounded local DNS data, protect stored preferences across upgrades, and provide correct pause/clear semantics.

**Architecture:** Keep DNS list compilation and Room writes strictly off the packet loop. Validate download bytes, protocol/redirects and rule limits before atomically publishing a new immutable rule snapshot. Introduce explicit Room migrations, bounded stats and a flush barrier for history deletion; respect users' privacy settings after restart.

**Tech Stack:** Kotlin, HttpURLConnection (existing), AtomicFile, Room, WorkManager, coroutines, JUnit, Android instrumentation, MockWebServer or a controlled in-process HTTP fixture.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- No cloud analytics or user-history uploads. Background filter downloads remain opt-in and must disclose that a filter-list host sees a request from the device.
- Preserve previous, last-known-good rule file until a replacement passes size, syntax, integrity and compile validation.
- Initial user-visible per-list bounds: 16 MiB transferred (hard cap), maximum 4 KiB physical line, maximum 250,000 accepted rules, maximum five HTTPS redirects; tune only if vetted real catalog requirements demonstrate a need. Reject rather than truncate.
- No arbitrary silent Room data loss. Domain-level statistics must have a clear maximum retention/row count and an understandable UI description.
- Do not log the raw rule-download URL with private credentials or any user's domain history to system logs.

## Review Focus

1. 16 MiB + 1 byte or an overlong single line must abort before unbounded memory growth (Task 1).
2. An HTTP downgrade redirect, invalid ZIP/plaintext or interrupted download must leave old active rules intact (Task 2).
3. Automatic updates on mobile data must not occur by default; user manual update can request one explicit exception (Task 2).
4. Clearing history while an asynchronous batch is in progress must never repopulate pre-clear domains (Task 3).
5. Upgrading v1/v2 schema must preserve log counts and user-rule migration inputs (Task 4).

---

### Task 1: Bounded HTTPS filter downloading

**Problem:** `ExtensionRepository.downloadAndSaveFilter()` uses unbounded `reader.forEachLine`, accepts redirect targets without strict scheme checks, and records bytes only after lines are read.

**Files:**
- Create: `app/src/main/java/com/unblocker/app/data/repository/BoundedFilterDownloader.kt`.
- Modify: `app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt`.
- Test: create `app/src/test/java/com/unblocker/app/data/repository/BoundedFilterDownloaderTest.kt`.

**Interfaces:** `suspend fun BoundedFilterDownloader.download(source: URL, destination: File, maxBytes: Long=16L*1024*1024, maxLineBytes: Int=4096, maxRules: Int=250000): DownloadResult` where result is a sealed success/failure reason (`TooLarge`, `InvalidSource`, `HttpFailure`, `InvalidRules`, `NetworkFailure`). Downloader accepts only HTTPS, at most five redirects to HTTPS, configured timeouts, a controlled size cap while reading, and no accidental internal/loopback target unless explicitly approved by a separate feature design.

- [ ] Write tests using mock URL/connection fixture: exactly-cap bytes accepted, cap+1 rejected; 4097-byte newline-free line rejected before allocating unbounded content; redirect to HTTP, missing Location and redirect loop rejected; timeout leaves old file unchanged.
- [ ] Run focused `./gradlew :app:testDebugUnitTest --tests 'com.unblocker.app.data.repository.BoundedFilterDownloaderTest'` and confirm expected initial failures.
- [ ] Implement size-limited stream processing before `BufferedReader.readLine()` can consume unlimited bytes; avoid optimistic `Content-Length` trust and enforce actual decoded bytes.
- [ ] Return structured error messages to `ExtensionsViewModel` without exposing private URLs/secrets; rerun tests.
- [ ] Commit `fix(extensions): bound and validate filter downloads`.

### Task 2: Atomic update, rollback, scheduling and data use

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt:reloadActiveRules/updateAll/downloadAndSaveFilter`.
- Modify: `app/src/main/java/com/unblocker/app/services/ExtensionUpdateWorker.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/extensions/ExtensionsViewModel.kt`.
- Test: `app/src/test/java/com/unblocker/app/data/repository/ExtensionRepositoryTest.kt` and create `ExtensionUpdateRollbackTest.kt`.

**Interfaces:** `sealed interface RuleUpdateState { data object Ready; data class Failed(val reason: String) }`; `updateExtension(id)` should only report success after complete validation and active rule-snapshot swap. Auto update requests `NetworkType.UNMETERED` by default; manual update may run on metered network only after user action.

- [ ] Write failing rollback tests for truncated stream, invalid rules, rename/write failure and cancel mid-update; old filter must still match after each failure.
- [ ] Download/compile in private temp file; validate minimum useful rule count and source integrity, then replace last-known-good `rules` with `AtomicFile` or equivalent atomic move on same volume. Avoid delete-then-rename gaps.
- [ ] Rebuild `ExtensionRuleStore` once per successful update set off the VPN hot path; no partial active state between lists. Keep filters unchanged on failure.
- [ ] Use ETag/Last-Modified when the server supports it, with an expiry/backoff policy and controlled `UNMETERED` periodic updates; do not repeatedly download unchanged lists.
- [ ] Run mocked tests and a device network interruption test, then commit `fix(extensions): atomic activation and user-controlled updates`.

### Task 3: Bounded aggregate logs and clear/pause correctness

**Problem:** `QueryLogSink` bounds raw event history but not `domain_stats`; log pause state is process-local; `clearHistory()` can race with in-flight `flushBatch()` and DAO upserts currently occur per domain.

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt`.
- Modify: `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/logs/LogViewModel.kt`.
- Modify: `data-store/src/main/java/com/unblocker/data/db/dao/DomainLogDao.kt`.
- Test: `app/src/test/java/com/unblocker/app/data/logs/QueryLogSinkTest.kt` and create `app/src/androidTest/java/com/unblocker/app/LogRetentionAndClearTest.kt`.

**Interfaces:** Introduce `suspend fun QueryLogSink.clearHistoryAndBarrier(): ClearResult` and a persisted preference `isQueryLoggingPaused`. The consumer must implement a monotonic queue generation and/or mutually exclusive DAO flush/clear transaction so items observed **before** clear cannot be reinserted after it completes. Suggested initial aggregate policy: max 10,000 domain rows and 30 days retention, documented and adjustable only through explicit product requirements.

- [ ] Write deterministic test that blocks a flush, invokes clear, releases flush and asserts pre-clear domain rows never reappear; test parallel enqueue during clear.
- [ ] Write test for paused logging surviving process restart and for raw event opt-in status not escalating silently after restart.
- [ ] Implement flush barrier with scoped synchronization, batch upserts in a Room transaction, and TTL/row pruning with bounded incremental work; do not block the DNS packet loop.
- [ ] Run unit + instrumentation tests; measure SQLite DB pages, CPU time and query accuracy under 10k-domain load; commit `fix(logs): bounded privacy-safe retention and atomic clear`.

### Task 4: Replace destructive Room schema migration

**Files:**
- Modify: `data-store/src/main/java/com/unblocker/data/di/DataStoreModule.kt` (remove `.fallbackToDestructiveMigration()`).
- Create: `data-store/src/main/java/com/unblocker/data/db/migration/UnblockerMigrations.kt`.
- Test: create `data-store/src/androidTest/java/com/unblocker/data/db/UnblockerMigrationTest.kt` with `MigrationTestHelper`.
- Review: `data-store/schemas/com.unblocker.data.db.UnblockerDatabase/1.json`, `2.json`.

**Interfaces:** Export explicit named `Migration(1,2)` and future `Migration(2,3)` only when the SQL delta is understood from checked-in schemas. Configure database builder `.addMigrations(...)`, preserving all user data. If irrecoverable corruption occurs, offer a clear user-facing recovery choice rather than silent data loss.

- [ ] Create v1 populated DB fixture and expected v2 query-result assertions for rule rows, log counts and whitelist.
- [ ] Run `./gradlew :data-store:connectedDebugAndroidTest` and confirm migration test behavior before removing fallback.
- [ ] Implement exact schema migration(s), then eliminate destructive fallback; verify exported schemas and `Room` entity indices match the migrated DB.
- [ ] Add test that upgrade/migration failures are surfaced rather than deleting all user rows; commit `fix(storage): preserve Room data across upgrades`.

## Exit gates

- No unbounded list download or line parsing. Oversized/corrupt/failed updates retain prior active rules and show a precise user message.
- Auto updates default to controlled unmetered, opt-in scheduling; no telemetry or undisclosed network traffic.
- Logs pause persists, clear is irreversible for pre-clear data at the logical DB level, stats retention bounded, and batch performance is measured.
- v1 -> v2 and next-version DB migrations preserve user data, validated via instrumentation.
