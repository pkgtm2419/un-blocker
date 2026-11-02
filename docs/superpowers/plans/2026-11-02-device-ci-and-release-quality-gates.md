# Device CI and Release Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent routing, signing, migration, privacy and compatibility regressions from shipping when JVM tests remain green.

**Architecture:** Extend existing GitHub Actions checks with an Android emulator VPN smoke stage and explicit release policy. A production tag cannot publish an artifact signed with the stable test key; runtime compatibility, native alignment and essential data migration tests become release gates. Keep production signing secrets only in protected environments.

**Tech Stack:** GitHub Actions, Gradle, Android emulator/ADB, CodeQL (already configured), JUnit, Android Lint, `apksigner`, `aapt2`, `zipalign`, `readelf`, Python/bash existing release scripts.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- Existing CI baseline `main` SHA `6fd537e34773e3f799e3685f68a0fbef761eb71c` has 260/260 JVM tests passing; **there is no connected device test job in current `android.yml`**.
- `v*` production tags must require the designated production signer; `test-v*` uses stable test signing and must be labeled nonproduction. Do not publish any artifact or trigger a release during plan implementation without authorized secrets and review.
- Preserve provenance: protected signing key, expected fingerprint, SHA-256 checksum and release tag/commit correspondence. Avoid making public the R8 `mapping.txt` unless intentionally approved.
- Production signer rotation affects installation upgrades, especially for v2.2.0 already distributed using test signing; plan user migration before enforcement.
- No disabling lint or adding `|| true` to mask real issues. Use Android emulator for correctness and physical phones for battery/performance.

## Review Focus

1. A production tag with no release signer or no pinned signer fingerprint must fail before publication (Task 2).
2. Existing test-signed v2.2.0 install upgraded with a different signer must follow a documented supported path; no false 'seamless upgrade' claim (Task 2).
3. A PR adding the public resolver host routes must fail the mandatory instrumented VPN test (Task 1).
4. A 4 KiB-aligned ARM64 native `.so` must fail the 16 KiB artifact gate unless an explicitly documented platform exception applies (Task 3).
5. A destructive Room upgrade or missing migration must not be silently ignored by CI (Task 4).

---

### Task 1: Add required connected VPN smoke checks

**Files:**
- Modify: `.github/workflows/android.yml`.
- Create: `.github/workflows/android-device-matrix.yml` (nightly/manual expanded matrix only).
- Modify: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt` based on routing plan.
- Test: `app/src/androidTest/java/com/unblocker/app/VpnNetworkRegressionTest.kt` (routing plan).

**Interfaces:** CI job `instrumentation` runs after successful build using a pinned Android emulator runner and `./gradlew :app:connectedDebugAndroidTest`; default API 35, additional API 26/30 and 16 KiB tests nightly or manually. Upload JUnit XML, logcat, emulator stdout and screenshots on failure, with privacy scrubbing.

- [ ] First run current instrumentation suite on API 35 in an isolated branch: capture actual failures caused by stale `10.10.0.1` references and public resolver routes; do not update tests merely to get green if production violates intended DNS-only semantics.
- [ ] Fix tests and route behavior in dependency plans and rerun until API 35 smoke is genuinely green.
- [ ] Add `instrumentation` as a required PR status in branch-protection settings after observing stable pass rate; let `Android checks` continue unit/lint/artifact tests.
- [ ] Add optional manual/nightly matrix for API 26, 30, 35 and a 16 KiB runtime, isolating unavailable hardware as BLOCKED, not PASS.
- [ ] Commit `ci(android): require live VPN routing smoke tests`.

### Task 2: Fail-closed release signing and explain upgrade constraints

**Files:**
- Modify: `.github/workflows/release.yml` (`Check signing secrets`, `Build signed APK`, `Verify APK before publishing` steps).
- Modify: `app/build.gradle.kts` release signing configuration.
- Modify: `scripts/release-policy.py`.
- Test: `scripts/test_release_policy.py` and `scripts/tests/test-verify-release.sh`.
- Create: `docs/release-signing-and-upgrade-policy.md`.

**Interfaces:** `release-policy.py secrets --build-type release` validates four `UB_RELEASE_*` variables plus an independently configured expected production signer fingerprint. `v*` tags only build `assembleRelease` under a protected production environment. If values are absent, abort; never fallback to `releaseTest` or stable test signer. Keep `test-v*` separate with test-specific artifact and prerelease label.

- [ ] Add failing policy tests: missing production key, missing signer fingerprint, wrong key, mismatched version/tag, test-signed production artifact, unverified tag ancestry, and attempted duplicate release asset.
- [ ] Change production `signingConfig` logic and workflow to error on partial/missing release configuration. Make release verification assert `!debuggable`, signer SHA-256 fingerprint, version, package ID and SHA256SUMS.
- [ ] Determine released v2.2.0 signer fingerprint versus intended production fingerprint; test Android upgrade semantics on isolated device. Document whether existing installs require reinstallation/data export or another official supported migration; never promise in-place upgrade if signatures differ.
- [ ] Keep R8 `mapping.txt` in a restricted release/debugging artifact instead of public GitHub Release assets unless release owner explicitly approves public mapping distribution.
- [ ] Run Python/bash tests and one **dry-run only** release build; verify no publish occurs, then commit `fix(release): require production signer and safe upgrade policy`.

### Task 3: Enforce lint, native alignment and artifact-size checks

**Files:**
- Modify: `app/build.gradle.kts` and `ml-engine/build.gradle.kts` (lint policy as applicable).
- Modify: `.github/workflows/android.yml` and `.github/workflows/release.yml`.
- Create: `scripts/check-release-native-libs.py` (from resource plan).
- Test: create `scripts/test_native_lib_gate.py`.

**Interfaces:** Native check reads APK ZIP metadata and ELF program headers; on 64-bit devices native PT_LOAD alignment is >= 16 KiB where required. Gate on unverified native dependencies, unexpected model/ABI changes, and explicit versioned size budget rather than marketing claims.

- [ ] Write tests with fixture libraries simulating aligned and unaligned PT_LOAD headers and APK paths; assert known incompatible `0x1000` warning is detected.
- [ ] Remove blanket `lint { abortOnError=false; ignoreWarnings=true }` suppression after each relevant finding is resolved/triaged, and do not leave `Aligned16KB`/critical security checks globally disabled.
- [ ] Add a release gate checking APK manifest target, expected version and `zipalign -c -P 16` plus ELF alignment; run emulator/device install on 16 KiB page configuration.
- [ ] Run `./gradlew testDebugUnitTest lintDebug lintRelease assembleReleaseTest`, then validate actual signed artifact and report deltas from v2.2.0.
- [ ] Commit `ci(quality): gate native compatibility and critical lint checks`.

### Task 4: Require migration, privacy, and deterministic DNS regressions

**Files:**
- Modify: `.github/workflows/android.yml` and `.github/workflows/release.yml` to reference new tests.
- Test: `data-store/src/androidTest/java/com/unblocker/data/db/UnblockerMigrationTest.kt`, `app/src/androidTest/java/com/unblocker/app/LogRetentionAndClearTest.kt`.
- Test: `app/src/test/java/com/unblocker/app/rules/RuleSetDifferentialTest.kt`, `app/src/test/java/com/unblocker/app/quality/BlockingRegressionCorpusTest.kt`.
- Create: `docs/qa/release-test-matrix.md`.

**Interfaces:** Stable required checks named `build` (JVM, lint, build, scripts) and `instrumentation` (device VPN, rules, DB migrations). Release publish job must depend on both green checks for the release commit/tag, or execute an equivalent gated set within its pre-publish workflow. Never infer a PR pass from a different commit SHA.

- [ ] Write/restore deterministic regression tests for DNS classification, allow rules, exclusions, false positives, migration data integrity and privacy clear semantics.
- [ ] Introduce a test-results summary that lists total, passed, failed, skipped, blocked and device/Android profile for each suite.
- [ ] Gate release on zero P0/P1 defects, all critical VPN/consent/migration tests and correct signing; do not use an arbitrary score to override an unmet gate.
- [ ] Run a representative tag dry-run after PR checks pass, verify artifacts and no publishing; commit `ci(release): require routing, migration and privacy evidence`.

## Exit gates

- `main` PRs cannot pass with broken public resolver routing or stale TUN DNS instrumentation.
- Production tag publishing fails if production signer/fingerprint missing and never generates a production-labeled test-signed APK.
- A 16 KiB compatibility gate and real install test demonstrate native support on representative devices.
- Migrated user data survives, privacy clears are durable, CodeQL/Gradle/JUnit checks are checked against the same commit.
- Publish a transparent release matrix, actual test metrics and installer compatibility notice before approving a new production version.
