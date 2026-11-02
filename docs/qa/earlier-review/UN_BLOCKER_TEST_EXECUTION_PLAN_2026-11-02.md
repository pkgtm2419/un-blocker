# Un-Blocker Android QA and Remediation Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` for code changes. Each task has a checkable test, evidence and acceptance gate. Confirm the design and branch strategy before modifying production code.

**Goal:** Establish reliable, reproducible, device-backed verification of Un-Blocker v2.2.0, resolve release and VPN routing risks, and enforce release gates for future versions.

**Architecture:** Preserve the existing five Gradle modules (`app`, `common`, `vpn-engine`, `data-store`, `ml-engine`). Add focused tests for the VPN/Android integration boundary, strengthen CI signing/lint rules and collect repeatable device metrics. Keep all sensitive data on the device or in sanitized test artifacts.

**Tech stack:** Kotlin 2.0.21, Android Gradle Plugin 8.10.1, Java 17, target SDK 35, Compose, JUnit, Espresso/AndroidX instrumentation, GitHub Actions, Python rule compiler, Android SDK tools, Perfetto and APK signing tools.

**Spec:** Existing `README.md`, `PRIVACY.md`, `docs/dns-bypass-matrix.md`, Android `VpnService` documentation and accompanying `UN_BLOCKER_QA_AUDIT_2026-11-02.md`.

## Global constraints

- Base all tests on `main` commit `6fd537e34773e3f799e3685f68a0fbef761eb71c` and state new head SHAs in every result.
- No actual production user traffic, data, credentials, URLs or history may enter screenshots, logs or test artifacts.
- Preserve `minSdk=26`, `targetSdk=35`, and user-approved privacy boundaries until a change is explicitly approved.
- Do not create a production GitHub Release or push a fix without explicit authorization.
- Label outcomes `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`, or `NOT APPLICABLE`. Include commands and evidence.
- Device performance measurement must use a consistent image, CPU thermal state, app build variant, and workload.

## Review focus: highest-risk inputs and conditions

1. Normal TCP/443 or UDP/443 to a popular DNS resolver IP during active VPN: must not be blackholed by the DNS-only tunnel.
2. VPN configured with local synthetic DNS addresses: upstream sanitizer must never choose those as upstream resolvers.
3. Android consent denied, revoked, or VPN restarted after network/boot transition: state must be accurate and DNS recovered.
4. Oversized or malicious third-party custom filter lists and redirects: must fail safely without unbounded downloads or disk growth.
5. Production tag with only test-signing credentials: must be rejected with no public artifact.

---

## Phase 0: Baseline / provenance

**Task 0.1 - Snapshot the repository and existing CI evidence**

- [ ] Capture `git rev-parse HEAD`, Gradle dependency versions, AndroidManifest and device matrix.
- [ ] Download existing GitHub Actions JUnit/lint/APK artifacts and preserve SHA-256 hashes.
- [ ] Run `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleReleaseTest` on an isolated branch/runner.
- [ ] Compare with CI baseline: 260 passing JVM tests and 7 `ml-engine` lint warnings across debug/release reports; failures of an updated checkout must be triaged, not hidden.
- [ ] Verify artifact hash and signature with SDK `apksigner`/`zipalign`.

**Acceptance:** Build and test commands are reproducible, results are correctly attributed to a commit, and discrepancies have documented causes.

## Phase 1: VPN correctness and resolver traffic

**Task 1.1 - Resolve public host-route/packet-loop mismatch (UB-QA-001)**

**Inspect:**
- `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt:168-327`
- `vpn-engine/src/main/java/com/unblocker/vpn/builder/VpnConfiguration.kt`
- `vpn-engine/src/main/java/com/unblocker/vpn/builder/UpstreamDnsServer.kt`
- `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt:217-271`

- [ ] Establish a failing device test for current route policy: compare TUN link prefixes to the app's documented DNS-only policy.
- [ ] Establish a controlled non-DNS TCP/443 and UDP/443 route/no-drop test with VPN on/off.
- [ ] Agree on either truly DNS-only split routing or correctly forwarding all intercepted protocols; implement minimal consistent behavior.
- [ ] Run device DNS A/AAAA resolution to a permitted domain and blocked domain; verify expected answer type and no unrelated traffic loss.
- [ ] Verify IPv4 and IPv6 route tables on Android 8, 11, 15+ as available.
- [ ] Run all VPN unit and device tests after the change.

**Acceptance:** No unwanted non-DNS traffic interception, blocked requests receive intended local responses, permitted requests resolve, and no VPN loops are observed.

**Task 1.2 - Update synthetic DNS address constants and tests (UB-QA-002/007)**

- [ ] Remove hardcoded stale `10.10.0.1`/`fd00:1::1` literals from active VPN instrumentation assumptions or explicitly retain them only as legacy-address test fixtures.
- [ ] Use shared local DNS address declarations for Builder, DNS sanitizer and tests.
- [ ] Add `DnsResolverPolicyTest` cases containing `10.111.222.2` and `fd00:1:fd00:1:fd00:1:fd00:2` expecting rejection.
- [ ] Fix `awaitVpnRouting()` and the DNS test socket to match the runtime Builder configuration.
- [ ] Ensure `vpnDoesNotPublishPublicResolverHostRoutes` tests actual intended behavior rather than a stale assertion.
- [ ] Run JVM and instrumented tests.

**Acceptance:** Real device tests do not assume obsolete synthetic DNS addresses, and all local tunnel addresses are excluded from upstream resolver lists.

**Task 1.3 - DNS protocol and bypass matrix**

- [ ] Unit: malformed DNS header, compressed pointer cycles, EDNS size, truncation, TCP fallback, CNAME alias, NXDOMAIN/SERVFAIL and response validation.
- [ ] Device: UDP DNS IPv4/IPv6, TCP DNS, A, AAAA, CNAME, HTTPS/SVCB, mixed queries and burst/coalescing.
- [ ] Network: Wi-Fi -> cellular -> Wi-Fi, captive portal, offline, DNS resolver change, upstream timeout, network loss during query.
- [ ] Private DNS Off, Automatic, Strict; custom DoH browser. Confirm limitations displayed clearly rather than implying blanket protection.
- [ ] Verify no packet history or sensitive URLs leak to Logcat.

**Acceptance:** A signed test evidence matrix describes interception, bypass, fallback and failure mode on every tested device/API/version.

## Phase 2: Production build and supply-chain integrity

**Task 2.1 - Fail-closed production signing (UB-QA-003)**

**Inspect:** `.github/workflows/release.yml`, `scripts/release-policy.py`, `scripts/test_release_policy.py` and `app/build.gradle.kts`.

- [ ] Write policy test: `vX.Y.Z` with no `UB_RELEASE_KEYSTORE_*` must fail before artifact publication.
- [ ] Write policy test: `test-vX.Y.Z` with stable test credentials may publish *only* as a prerelease.
- [ ] Confirm production APK signature against a separately pinned production signer; do not let fallback use the test signer.
- [ ] Make mainline Gradle `release` build fail signing/verification for distribution when a production signing configuration is missing.
- [ ] Verify `android:debuggable=false`, minification and R8 rules on actual artifact, not only Gradle configuration.
- [ ] Test no production release is created after secret or signing validation failure.

**Acceptance:** Production tags cannot publish test-signed APKs and signer continuity is verifiable.

**Task 2.2 - Native 16 KiB alignment (UB-QA-004)**

- [ ] Replace or upgrade TensorFlow Lite dependency shipping misaligned native binaries.
- [ ] Run Android Lint without suppression; resolve `Aligned16KB` warnings.
- [ ] `readelf -W -l` for arm64 native libraries must show 16 KiB-compatible PT_LOAD alignment.
- [ ] `zipalign -c -P 16 -v 4 app/build/outputs/apk/releaseTest/ub-blocker-2.2.0.apk` must succeed for the evaluated build path.
- [ ] Install and launch on API 35+ 16 KiB-page emulator (`adb shell getconf PAGE_SIZE` => `16384`).
- [ ] Execute TFLite work and confirm no native loader/linker errors.

**Acceptance:** Native and packaged-library alignment confirmed, plus successful 16 KiB environment smoke test.

**Task 2.3 - Lint and CI gate policy (UB-QA-005)**

- [ ] Remove broad `abortOnError=false`/`ignoreWarnings=true` settings unless individually justified.
- [ ] Audit disabled lint checks, especially checks relevant to TLS and native compatibility.
- [ ] Make lint artifacts available in CI and enforce zero unresolved critical/high findings.
- [ ] Keep CodeQL jobs and GitHub Actions pinning; review reported alerts separately from job status.

**Acceptance:** CI fails for release-blocking findings and exports a transparent lint/security inventory.

## Phase 3: Android instrumentation and compatibility

**Task 3.1 - Make device integration tests a CI gate**

- [ ] Add emulator-based `:app:connectedDebugAndroidTest` to Android checks on PRs and main branch, with an emulator that supports VPN consent/AppOps operations.
- [ ] Resolve stale VPN fixture addresses; compare route result to actual intended policy.
- [ ] Run scenarios: allow/block domain, persist rule, reboot/boot receiver, consent denial, permission revoke, service restart, DNS network transition.
- [ ] Add UI Automator/Compose smoke tests for power switch, filter selection, extensions, logs, errors and settings navigation.
- [ ] Add physical-device acceptance for API 26, API 30/31, API 35+, plus 16 KiB-page environment where available.

**Acceptance:** Instrumentation jobs run on PRs with stored XML, screenshots and Logcat on failure, and no soft-green skipped device tests.

**Task 3.2 - UX and accessibility**

- [ ] Verify first-install consent and foreground-service notification permissions on API 33+.
- [ ] Verify state transitions STARTING/RUNNING/ERROR/STOPPED, including denial, retry, stop and reboot.
- [ ] Test portrait, landscape, large text, dark/light, foldable/tablet as supported.
- [ ] Check TalkBack labels, focus ordering, target size and UI error messages.
- [ ] Test data deletion, allow/block precedence, extension update notifications, and unsupported Private DNS warnings.

**Acceptance:** Every user-visible feature has at least one positive/negative scenario with screenshots.

## Phase 4: Extensions, data storage and privacy

**Task 4.1 - Bound extension downloads (UB-QA-008)**

- [ ] Add mocked HTTP 200/302/404/500 response tests, relative/absolute redirect tests and HTTPS downgrade rejection.
- [ ] Test maximum response bytes, maximum line length, maximum rule count, decompression or oversized file, interrupted read and disk-full behavior.
- [ ] Verify safe atomic replacement preserves the previous list on invalid or truncated download.
- [ ] Ensure third-party list sources and consent disclosure match product privacy claims.

**Acceptance:** Untrusted extension input cannot cause unbounded storage/processing and failure does not corrupt active rules.

**Task 4.2 - Room logs and learning privacy**

- [ ] Test migration from schema 1 to 2 with actual instrumented Room DB upgrade.
- [ ] Test opt-in chronological log mode vs default aggregated mode, retention (24 hours/1000 event cap), pause, clear and restart persistence.
- [ ] Verify no raw user DNS data leaves device, is backed up, or enters Logcat.
- [ ] Test lost/invalid Keystore, v1/v3 migration, key reset, rule precedence and failed writes.
- [ ] Confirm privacy documentation mirrors actual data retention and opt-in.

**Acceptance:** Data lifecycle is demonstrably private, bounded and recoverable.

**Task 4.3 - Batch jobs and battery constraints (UB-QA-006)**

- [ ] Test nightly `WorkScheduler` scheduling at 01:00 across time zones/DST changes and charging/idle states.
- [ ] Use WorkManager test APIs to assert scheduling inputs and constraints.
- [ ] Verify backlog fallback triggers once without unbounded enqueued work.
- [ ] Measure standby wakeups, Worker retries and thermal impact in Doze mode.

**Acceptance:** Work is eventually scheduled and bounded on devices that do not simultaneously report idle and charging.

## Phase 5: Security and static analysis

**Task 5.1 - MASVS-oriented audit**

- [ ] Verify AndroidManifest service permission/export rules, PendingIntent mutability, boot receiver and backups.
- [ ] Validate HTTP/HTTPS, TLS trust, redirect policy, hardcoded domains/credentials and permission usage.
- [ ] Review HMAC Keystore lifetime, old-file cleanup and sensitive local data storage.
- [ ] Review external rule-list provenance and integrity checks.
- [ ] Perform source dependency vulnerability scan and review all actionable advisories.
- [ ] Validate release signing and software bill of materials where required.

**Acceptance:** Risk register contains severity, evidence, exploitability assumptions and mitigations. No unreviewed high-severity security concern.

## Phase 6: Resource and performance testing

**Task 6.1 - Controlled performance baseline**

Collect *measured*, never invented, results. Run on at least one lower-end device and a modern ARM64 device, with equivalent Wi-Fi conditions. Repeat key runs 5+ times and record medians/p95.

| Scenario | Metric | Method | Release criterion |
|---|---|---|---|
| Cold start | ms, p50/p95 | Macrobenchmark/Perfetto | Agree baseline and regression budget |
| VPN start/stop | ms, success count | Instrumented device test | No hang/incorrect state |
| DNS allowed A/AAAA | latency ms p50/p95/p99 | Local controlled resolver + packet trace | Bounded against baseline |
| DNS blocked A/AAAA | latency ms p50/p95/p99 | Local controlled resolver + packet trace | Bounded against baseline |
| 1k concurrent/burst DNS requests | loss, memory and CPU | Controlled traffic generator | No unchecked drop or unbounded growth |
| 10-min foreground idle | PSS, CPU %, wakeups | `dumpsys meminfo`, Perfetto | No unexplained sustained work |
| 30-min active blocking | memory trend, GC, CPU, thermal | Profiler/Perfetto | No confirmed leak/ANR |
| 24h typical device usage | battery delta, wakeups, retries | Battery Historian/Android vitals | Within agreed power budget |
| Background/Doze | job executions and wakeups | WorkManager/dumpsys | Respect user and OS constraints |
| DNS cache and rules corpus | hit rate, matches/sec, memory | JMH-like JVM microbenchmark plus device | Regression controlled |

Example device commands:

```bash
adb shell getconf PAGE_SIZE
adb shell dumpsys meminfo com.unblocker.app
adb shell dumpsys batterystats --charged com.unblocker.app
adb shell dumpsys gfxinfo com.unblocker.app framestats
adb shell settings get global private_dns_mode
adb logcat -d -v time
```

**Acceptance:** Device, OS, commit, build variant, scenario and full units accompany each published performance number.

## Phase 7: Release validation and documentation

**Task 7.1 - Verify end-to-end package release**

- [ ] On clean supported devices install the signed APK; accept VPN consent and run smoke test.
- [ ] Upgrade from previous published APK; verify signing continuity and preserved private settings/migrations.
- [ ] Verify `SHA256SUMS`, APK signer, non-debuggable status and R8 mappings.
- [ ] Verify README documentation and remove stale `10.10.0.1`, privacy route claims and missing links.
- [ ] Produce consolidated final defect and regression report with exact environment metadata.

**Acceptance:** No P0/P1 unresolved release blockers; explicit waiver process for P2; all mandatory gates backed by device/CI evidence.

---

## High-value test cases (starting matrix)

| ID | Test case | Expected | Priority |
|---|---|---|---|
| VPN-001 | Consent denied then start | Protection does not claim RUNNING | P1 |
| VPN-002 | Grant consent then start | RUNNING and synthetic DNS routes valid | P1 |
| VPN-003 | Block known ad domain over UDP/IPv4 | Local blocked answer | P1 |
| VPN-004 | Allow benign domain over UDP/IPv6 | Valid response via configured resolver | P1 |
| VPN-005 | DNS-over-TCP with truncated upstream | Protected TCP fallback | P1 |
| VPN-006 | TCP/443 to routed public DNS IP | No unintended drop/interception | P1 |
| VPN-007 | UDP/443 to routed public DNS IP | No unintended drop/interception | P1 |
| VPN-008 | Query local tunnel resolver in upstream list | Local resolver rejected | P1 |
| VPN-009 | Switch Wi-Fi to cellular during requests | Resolver refresh without loops | P1 |
| VPN-010 | Revoke VPN permission mid-run | Stops and reports accurate state | P1 |
| VPN-011 | Boot with auto-start off | Remains off | P1 |
| VPN-012 | Boot with auto-start on and consent | Restarts where OS allows | P1 |
| VPN-013 | Private DNS strict mode | Transparent bypass warning | P1 |
| VPN-014 | Android 15+ 16 KiB-page device | Install and native initialization work | P1 |
| REL-001 | Production tag without production keystore | Publish denied | P1 |
| REL-002 | Production APK with test signature | Rejected | P1 |
| REL-003 | Prerelease tag with test keystore | Explicit prerelease only | P2 |
| DB-001 | Schema 1 -> 2 migration | No data corruption | P1 |
| PRIV-001 | Default query log mode | No raw event history without opt-in | P1 |
| PRIV-002 | Logs pause / clear | No retained records after clear | P1 |
| PRIV-003 | Keystore unavailable during v3 learning | Fails safely / documented fallback | P1 |
| EXT-001 | Redirect to HTTP or unrelated origin | Reject or enforce explicit trust policy | P2 |
| EXT-002 | Oversized blocklist and disk full | Safe bounded failure without partial replacement | P1 |
| ML-001 | Idle + charging constraints never both available | Bounded fallback runs | P2 |
| PERF-001 | Active VPN 24h | No ANR and within defined battery budget | P1 |
| UX-001 | Font scaling and TalkBack | All controls readable/actionable | P2 |

## Deliverables

1. `docs/qa/feature-inventory.md` - verified module/feature inventory.
2. `docs/qa/test-matrix.md` - fully populated conditions and statuses.
3. `docs/qa/security-audit.md` - MASVS controls plus severity and evidence.
4. `docs/qa/performance-baseline.md` - raw measurements and device metadata.
5. `docs/qa/ci-report.md` - JUnit/lint/CodeQL and artifact provenance.
6. `docs/qa/defect-register.md` - reproduction and fix verification.
7. `docs/qa/release-readiness.md` - clear release gate decision.

**Status of this plan:** Drafted from repository analysis; not yet implemented in repository. There is no claim that the unrun steps passed. No GitHub files, branches or releases were changed as part of this audit.
