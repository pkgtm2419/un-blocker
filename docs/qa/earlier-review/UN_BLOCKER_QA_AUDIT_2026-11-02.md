# Un-Blocker v2.2.0 - Evidence-Based QA and Security Audit

**Audit date:** 2026-11-02
**Repository:** https://github.com/pkgtm2419/un-blocker
**Branch:** `main`
**Commit:** `6fd537e34773e3f799e3685f68a0fbef761eb71c`
**Version:** `2.2.0` (`versionCode 27`)
**Package:** `com.unblocker.app`
**Android:** `minSdk 26`, `targetSdk 35`, `compileSdk 35`
**Framework:** Kotlin 2.0.21, Compose, Coroutines, Hilt, Room, WorkManager, TensorFlow Lite.
**Scope:** Read-only GitHub code inspection, inspection of existing CI workflows and logs, JUnit XML/lint report extraction, and APK static inspection of the CI `releaseTest` artifact. No Android emulator/physical device was available, and a direct GitHub clone was unavailable due to environment network/DNS restrictions.

## Executive result

**Decision: NOT READY FOR UNCONDITIONAL PRODUCTION APPROVAL.** Existing CI jobs passed, but release signing policy, VPN route correctness, unexecuted/broken instrumentation tests, and native page-size compatibility need remediation or verification. This is not a claim that those functional defects were reproduced on a device.

### Verification dashboard

| Area | Evidence | Result |
|---|---|---|
| Android CI workflow | https://github.com/pkgtm2419/un-blocker/actions/runs/41342081066 | SUCCESS |
| Unit tests | Archived JUnit XML from CI `reports-41342081066` | 260 tests, 0 failures, 0 errors, 0 skipped; 62 suites |
| App unit tests | JUnit XML | 254/254 passed |
| Data-store unit tests | JUnit XML | 3/3 passed |
| VPN-engine unit tests | JUnit XML | 3/3 passed |
| Android lint | 10 HTML reports from CI | Build succeeded; `ml-engine` reports 4 debug warnings and 3 release warnings, all other modules no listed issues |
| CodeQL | https://github.com/pkgtm2419/un-blocker/actions/runs/41342080818 | Three analysis jobs completed successfully (actions/java-kotlin/python); findings inventory not independently retrieved |
| Published release workflow | https://github.com/pkgtm2419/un-blocker/actions/runs/41342112852 | SUCCESS; production-labeled v2.2.0 used `releaseTest`/test signing according to job logs |
| APK output | Archived CI APKs | Debug 37,485,248 bytes; releaseTest 19,797,964 bytes; artifacts inspected |
| Native ELF alignment | `readelf -W -l` on `lib/arm64-v8a/libtensorflowlite_jni.so` extracted from CI releaseTest APK | PT_LOAD alignment `0x1000` (4 KiB), not 16 KiB |
| Instrumentation tests | `.github/workflows/android.yml` and `app/src/androidTest/...` | Present in source, NOT run by current Android CI workflow |
| Device runtime, CPU, RAM, battery, startup, DNS leakage | No emulator, device, or Perfetto traces | NOT RUN / BLOCKED BY ENVIRONMENT |

**Evidence retention:** GitHub Actions test reports: https://github.com/pkgtm2419/un-blocker/actions/runs/41342081066

## Application feature inventory

- Local DNS filtering of advertising, tracker and optional adult-content domains via Android VpnService.
- IPv4/IPv6 DNS interception, DNS-over-UDP and TCP response handling, upstream resolver forwarding, cache, CNAME safety, response validation and packet checksums.
- Deterministic rule lists (bundled/compiled), allow/block rules, custom extensions and optional extension updates.
- On-device heuristic reputation learning with Keystore-backed keyed identifiers and local evidence retention.
- Compose dashboard/power state, filter settings, extension manager and local logs panel.
- Optional local DNS logs (aggregate statistics and separately opt-in chronological history), pause/clear operations.
- Boot receiver, network callbacks, foreground notification, health-check worker.
- No account backend or user authentication was found; account/password/RBAC tests are NOT APPLICABLE to this architecture.

## Prioritized findings

### UB-QA-001 | P1 High | Public-resolver IP routes capture more than DNS
**Evidence:** `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt` lines 181-204 and `vpn-engine/src/main/java/com/unblocker/vpn/builder/VpnConfiguration.kt` lines 35-60 both add `/32` IPv4 and `/128` IPv6 routes for 8.8.8.8, 1.1.1.1, etc. The packet loop (service lines 318-327) processes DNS and drops packets it cannot interpret as DNS; `DnsPacketUtil.buildErrorResponseIfApplicable` only handles UDP destination port 53. The privacy statement says the VPN does *not* add public resolver host routes (`PRIVACY.md`, Network traffic).
**Impact:** An IP prefix route is protocol/port-agnostic; non-DNS HTTPS, TLS, ICMP, or other traffic to the routed addresses can enter the TUN interface without an appropriate general-traffic forwarding path. This is a code-supported interference risk, not a device-reproduced outage.
**Test:** With VPN enabled, send controlled TCP/443 and UDP/443 traffic to 1.1.1.1/8.8.8.8 and compare to VPN disabled; verify no unexpected capture/drop. Check VPN link routes on device.
**Remediation:** Make the routing and packet-forwarding strategy internally consistent. For DNS-only split tunnel, do not route third-party public DNS IP prefixes unless all traffic to them is handled correctly; document scope and limits and add instrumentation coverage.
**Source:** https://github.com/pkgtm2419/un-blocker/blob/main/app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt#L181-L204

### UB-QA-002 | P1 High | Instrumented regression test contradicts implementation and contains old tunnel addresses
**Evidence:** `LocalPrivacyAndVpnTest.kt` line 108 waits for DNS server `10.10.0.1`; line 233 sends a query to `10.10.0.1`; line 252 onward asserts that public DNS host routes do not exist. Current VPN service advertises `10.111.222.2` and *adds* public host routes. CI workflows do not execute `connectedDebugAndroidTest`.
**Impact:** A real-device test run is likely to fail even when the JVM suite passes, and the routing behavior lacks a functioning CI gate. This is a deterministic contradiction in checked-in test assertions vs source, but no emulator result was obtained.
**Test:** Run `./gradlew connectedDebugAndroidTest` on an API 26+ emulator with VPN consent and inspect failed assertions. Update stale test addresses, maintain test of intended routing policy.
**Sources:** https://github.com/pkgtm2419/un-blocker/blob/main/app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt#L101-L110 ; https://github.com/pkgtm2419/un-blocker/blob/main/app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt#L217-L269

### UB-QA-003 | P1 High | Production-tag release published using test-signing fallback
**Evidence:** Release workflow's `publish` job (`release.yml`, roughly lines 110-208) falls back from `assembleRelease` to `assembleReleaseTest` if the production keystore is unavailable. For v2.2.0 the release job log shows an empty release key path, `assembleReleaseTest`, and verification against the test-signing fingerprint. The GitHub Release notes still call it a `Production release`.
**Impact:** Public production artifacts can be signed using the test credential. This is a release provenance/policy failure and could affect update-signing continuity.
**Test:** Force a production-tag dry-run with production signing secrets absent and assert that publication is denied. Inspect produced signature using `apksigner verify --print-certs` and compare to a separately approved production certificate.
**Remediation:** Fail closed for `vX.Y.Z` production tags if the production signing key/fingerprint is missing. Allow test signing only for explicit prerelease tags, with clearly labeled prerelease artifacts.
**Sources:** https://github.com/pkgtm2419/un-blocker/blob/main/.github/workflows/release.yml ; https://github.com/pkgtm2419/un-blocker/actions/runs/41342112852

### UB-QA-004 | P1 High | 16 KiB native-library page-size compatibility warning confirmed
**Evidence:** Archived `ml-engine` lint report shows `Aligned16KB` warnings involving `org.tensorflow:tensorflow-lite:2.16.1`. Extracted `arm64-v8a/libtensorflowlite_jni.so` in the CI releaseTest APK has `PT_LOAD` alignment `0x1000` (4096), not 16384. Google Android documentation requires compatible native libraries on 16 KiB-page devices.
**Impact:** Possible install/runtime incompatibility on Android devices using 16 KiB pages; verify on a 16 KiB system image before shipping to such devices.
**Test:** `adb shell getconf PAGE_SIZE` must show 16384; check `zipalign -c -P 16 -v 4 <apk>`; run app startup and TFLite initialization.
**Remediation:** Update/replace/rebuild the incompatible TensorFlow Lite native dependency and verify ELF and APK alignments.
**Reference:** https://developer.android.com/guide/practices/page-sizes

### UB-QA-005 | P2 Medium | Lint warnings are explicitly non-blocking
**Evidence:** `app/build.gradle.kts` sets `abortOnError = false`, `ignoreWarnings = true`, `checkReleaseBuilds = false` and disables several lint checks. `ml-engine` reports actual warnings in debug/release despite green workflow.
**Impact:** Important regressions can be present while CI passes; security and compatibility assurance is weaker.
**Remediation:** Review and enable relevant lint gates; keep scoped, justified suppressions only. Export/upload lint findings to a quality dashboard.
**Source:** https://github.com/pkgtm2419/un-blocker/blob/main/app/build.gradle.kts

### UB-QA-006 | P2 Medium | Background ML nightly work has conflicting constraints
**Evidence:** `ml-engine/src/main/java/com/unblocker/ml/worker/WorkScheduler.kt`, lines 54-56 combines `requiresCharging(true)` and `requiresDeviceIdle(true)`; Android Lint reports `IdleBatteryChargingConstraints` (some devices never satisfy both).
**Impact:** A nightly batch may be delayed indefinitely on some devices.
**Remediation:** Use a constraint combination actually satisfiable on target devices, retain a bounded fallback, and test WorkManager scheduling under charging/idle/Doze conditions.
**Source:** https://github.com/pkgtm2419/un-blocker/blob/main/ml-engine/src/main/java/com/unblocker/ml/worker/WorkScheduler.kt#L54-L60

### UB-QA-007 | P2 Medium | Tunnel DNS sanitization uses stale addresses
**Evidence:** `DnsResolverPolicy.kt` excludes `10.10.0.1` and `fd00:1::1`, while current service config advertises `10.111.222.2` and `fd00:1:fd00:1:fd00:1:fd00:2`. `DnsResolverPolicyTest.kt` tests only the old addresses.
**Impact:** If an upstream resolver set ever includes the local tunnel address, it may not be filtered, risking a resolver loop/failure; actual occurrence depends on the system's network link properties.
**Remediation:** Define tunnel DNS addresses in a shared constant, sanitize the real current tunnel addresses, and add regression tests for both address families.
**Sources:** https://github.com/pkgtm2419/un-blocker/blob/main/app/src/main/java/com/unblocker/app/services/DnsResolverPolicy.kt ; https://github.com/pkgtm2419/un-blocker/blob/main/app/src/test/java/com/unblocker/app/services/DnsResolverPolicyTest.kt

### UB-QA-008 | P2 Medium | Custom extension download has no observable size/rule bounds
**Evidence:** `ExtensionRepository.downloadAndSaveFilter` streams arbitrary lines from configured URLs to a `.rules.tmp` file with no explicit total-byte, line-length, or parsed-rule limits before replacing the current rules file. Redirect URL validation is not explicit.
**Impact:** Large, compromised or incorrectly configured lists may use excessive disk, processing time or memory; source trust and failure recovery need testing. This is an input-hardening issue, not a confirmed exploit.
**Remediation:** Require trustworthy HTTPS endpoints, validate redirect destinations, enforce download, line and rule limits, use safe atomic replacement, reject non-success HTTP statuses, and test truncation/oversize behavior.
**Source:** https://github.com/pkgtm2419/un-blocker/blob/main/app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt

### UB-QA-009 | P3 Low | README and privacy documentation lag implementation
**Evidence:** README and Private DNS matrix still mention tunnel address `10.10.0.1`, but code uses `10.111.222.2`. README links to `docs/performance.md`, `docs/dns-scope.md`, and `docs/phase5-verification.md` which were absent from the reviewed main-branch Git tree. Privacy policy says no public-resolver host routes whereas code includes them.
**Impact:** Incorrect test instructions and user-facing claims; stale links hamper audit reproducibility.
**Remediation:** Update documentation from actual code and generated reports, and add link/test consistency checks.
**Sources:** https://github.com/pkgtm2419/un-blocker/blob/main/README.md ; https://github.com/pkgtm2419/un-blocker/blob/main/PRIVACY.md

## Evidence vs inference

- CONFIRMED: Main CI and release workflows completed successfully, 260 JUnit tests passed, lint warnings existed, `releaseTest` signing fallback was used, stale test literals and conflicting route assertions exist, and the inspected TFLite ELF loads at 4 KiB alignment.
- STATIC RISK (DEVICE REPRODUCTION NEEDED): user traffic blackholing at third-party DNS IPs, route leaks, DNS resolver loops, failed instrumentation tests, or runtime 16 KiB incompatibility.
- NOT RUN: Android emulator/physical-device tests, runtime VPN/DNS checks, dynamic security scans, battery/memory/CPU/network/thermal benchmarks, vulnerability database resolution, and production credential/keystore tests.

## Architecture sketch

`app` (Compose UI, foreground VpnService, filtering logic, local logs and extensions) -> `vpn-engine` (packet parsing and tunnel setup), `data-store` (Room, rules and logs), `ml-engine` (local ML/WorkManager), `common` (shared models and dispatchers). The service discovers Android-configured upstream resolvers and sends allowed DNS via protected sockets, while local policy decides which DNS responses to block. This is a DNS filtering utility, not an anonymity VPN.

## Suggested release gates

- No public-resolver non-DNS traffic loss, with device evidence.
- Instrumentation tests run and pass on supported API levels; no stale tunnel routes or addresses.
- Production `v*` tag refuses test-only signing; signer pin is documented and validated.
- 16 KiB page-size APK and native-library compatibility verified, plus 16 KiB runtime smoke tests.
- All required lint error checks blocking and relevant warnings reviewed.
- DNS behavior (UDP/TCP, IPv4/IPv6, Private DNS, DoH bypass warnings, roaming, captive portal) characterized on real devices.
- CPU, memory, power, DNS latency p50/p95/p99 and background reliability baselines measured on at least one low-end and one current device.
- No high/critical security issues unresolved or formally unaccepted.

**Repository changes made in this audit:** NONE. No new CI run was triggered. The test-execution plan is provided separately.
