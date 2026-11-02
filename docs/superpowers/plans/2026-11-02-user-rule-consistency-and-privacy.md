# User Rule Consistency and Privacy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Allow/Block control change the actual VPN decision immediately, persist private user intent, and migrate legacy plaintext rules without silent data loss.

**Architecture:** The existing `DeviceLearning.allowlist/blocklist` and `DecideBlockingUseCase` remain the authoritative active enforcement path. A new injected `UserRuleGateway` mediates all UI-originated commands, with a migration adapter for old Room user-rule rows and an observable success/failure contract.

**Tech Stack:** Kotlin, Hilt, Room, Android Keystore HMAC storage, JUnit/AndroidJUnit4, coroutines.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- Base SHA `6fd537e34773e3f799e3685f68a0fbef761eb71c`; preserve minSdk 26, targetSdk 35, Java 17, Kotlin 2.0.21.
- No telemetry, no raw rule exports or automatic upload. Existing opaque HMAC identifiers are not reversible; explain UI limitations honestly.
- The private user allow rule must take precedence over static, extension and learned blocking. One-tap rules must survive process recreation while private storage/Keystore works.
- Never run disk I/O on VPN packet thread, and do not silently swallow a failed rule write or report success before durable commit.

## Review Focus

1. Allowing `doubleclick.net` from Logs must affect `ContentFilterEngine.analyzeAndFilter()` immediately (Task 1).
2. Blocking `wikipedia.org` from Logs must work even if normal ads/adult switches are off (Task 1).
3. A saved rule must survive process death and device restart without plaintext history in the new rule file (Task 2).
4. A failed Keystore or storage migration must leave a user-visible error without false success (Task 2).
5. Conflicting allow/block decisions must remain deterministic and respect the allow-first override policy (Task 3).

---

### Task 1: Connect Logs allow/block commands to the active filter

**Problem:** `LogViewModel` invokes `FilterRepository.whitelistDomain/blacklistDomain`, which persists `WhitelistEntity` and `BlockRuleEntity` to Room and rebuilds `OffHeapRuleEngine`. Production `ContentFilterEngine` instead checks `DeviceLearning` private domain sets. These are separate authorities.

**Files:**
- Create: `app/src/main/java/com/unblocker/app/data/repository/UserRuleGateway.kt`.
- Create: `app/src/main/java/com/unblocker/app/data/repository/DeviceUserRuleGateway.kt`.
- Modify: `app/src/main/java/com/unblocker/app/di/AppModule.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/logs/LogViewModel.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/logs/LogViewerScreen.kt` for command status/error feedback.
- Test: create `app/src/test/java/com/unblocker/app/ui/logs/UserRuleActionTest.kt`.
- Test: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt`.

**Interfaces:** `interface UserRuleGateway { suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult }`. `sealed interface RuleUpdateResult { data object Applied; data class Failed(val reason: String) }`. Implementation calls `DeviceLearning.setUserRule(context, normalizedDomain, feedback)` off the main thread; validate `DomainName.normalizeUserEntry()` and surface storage failures. Bind with Hilt in `AppModule`.

- [ ] Write `UserRuleActionTest`: a fake gateway receives `UserFeedback.ALLOW` for `doubleclick.net` and `BLOCK` for `wikipedia.org`; blank/malformed domain yields failure and no mutation.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.unblocker.app.ui.logs.UserRuleActionTest'` and confirm initial failure.
- [ ] Implement gateway and replace both `FilterRepository` calls in `LogViewModel`; use explicit success/error messages, not fire-and-forget optimistic UI.
- [ ] Instrumentation: when the VPN runs, assert `ContentFilterEngine.analyzeAndFilter("doubleclick.net")` becomes allowed after Logs action and a formerly allowed test domain becomes blocked. Then query both domains over TUN.
- [ ] Commit `fix(rules): make Logs commands use active private rule store` after JVM and device tests pass.

### Task 2: Migrate legacy user overrides and protect private storage

**Files:**
- Create: `app/src/main/java/com/unblocker/app/data/repository/LegacyUserRuleMigration.kt`.
- Modify: `app/src/main/java/com/unblocker/app/UnblockerApplication.kt` to run migration away from the hot path before rules are declared ready.
- Review: `data-store/src/main/java/com/unblocker/data/repository/FilterRepository.kt`, `BlockRuleDao.kt`, `WhitelistDao.kt`.
- Test: create `app/src/androidTest/java/com/unblocker/app/LegacyUserRuleMigrationTest.kt`.

**Interfaces:** `suspend fun LegacyUserRuleMigration.migrateIfRequired(): MigrationResult`; implementation must identify Room `WhitelistEntity` and `BlockRuleEntity` where `source == USER`, add those rule values through `UserRuleGateway`, and mark migration complete **only after** each private write was confirmed. A durable versioned migration marker is set in private settings.

- [ ] Write migration test with a v2 Room DB containing one user allow, one user block and one non-user static/ML row; assert only user entries are transferred to the private authority.
- [ ] Write interruption test: simulate cancellation after one transfer, rerun and confirm idempotent, non-destructive migration and no silently lost rule.
- [ ] Implement migration before UI reports user rules synchronized; after successful transfer, delete only migrated legacy plaintext `USER` override rows, not unrelated logs or rules. Document that this is logical deletion, not guaranteed forensic flash erasure.
- [ ] Verify no raw private-rule files are created; assert HMAC identifiers do not equal the input domain and private Keystore is used when available.
- [ ] Run instrumented migration, restart, and VPN-domain tests; commit `fix(privacy): migrate legacy overrides without losing user intent`.

### Task 3: Lock precedence and allow immediate reversals

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt` only if tests identify mismatches.
- Modify: `app/src/main/java/com/unblocker/app/logic/analysis/DeviceLearning.kt` only if needed for idempotence.
- Test: `app/src/test/java/com/unblocker/app/domain/DecideBlockingUseCaseTest.kt`.
- Test: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt`.

**Interfaces:** Preserve `DeviceLearning.setUserRule(context, domain, UserFeedback)`; add gateway's `set(..., UserFeedback.NONE)` for removal. Use an explicit test table, not duplicated production control paths.

- [ ] Add tests for user allow/block precedence over static ad and learned evidence, strict domain matching, CNAME alias behavior and toggles.
- [ ] Verify exact-domain allow does not accidentally allow sibling subdomains, and exact-domain block does not extend beyond intended policy.
- [ ] Test `ALLOW -> BLOCK -> NONE` transitions with running VPN, cache already populated and process restart. No stale DNS cache decision may override the new rule.
- [ ] Make minimal changes if tests fail; run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`.
- [ ] Commit `test(rules): enforce explicit user override precedence`.

## Exit gates

- One-tap Logs actions change the production VPN decision within the next relevant DNS query and survive process restart.
- No split-brain Room/DeviceLearning authority for new commands; legacy rules migrated or explicitly reported as not migrated.
- No new plaintext persistent rule files; storage failure does not report success.
- Clear user-facing messages for modified, failed and removed rules. Full JVM and actual Android VPN tests pass.
