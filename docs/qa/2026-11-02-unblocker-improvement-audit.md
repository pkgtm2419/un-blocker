# Un-Blocker v2.2.0 Improvement Audit and Verification Record

**Repository:** https://github.com/pkgtm2419/un-blocker  
**Branch / commit:** `main` / `6fd537e34773e3f799e3685f68a0fbef761eb71c`  
**Review date:** 2026-11-02  
**Evidence sources:** GitHub source inspection; GitHub Actions run `41342081066` and its downloaded artifacts; release run `41342112852`; CodeQL run `41342080818`.

## 1. Verification scope

| Check | Result | Meaning |
|---|---|---|
| Existing JUnit XML | **PASS: 260 tests, 62 suites; 0 failures, errors or skips** | Existing JVM tests executed in GitHub Actions; **not** full app QA |
| Build and release CI | **PASS: completed workflows** | Jobs completed and artifacts published, not proof of real-network function |
| ReleaseTest APK measured | **PASS: archive inspected** | 19,797,964 bytes package; 8 native libraries / 14,693,752 bytes (74.22% of APK size) |
| ARM64 TFLite ELF segment alignment | **CONFIRMED: three PT_LOAD segments align 0x1000 (4 KiB)** | Compatibility risk for native code on 16 KiB-page devices |
| Packaged model file search | **CONFIRMED: no `.tflite` or `.onnx` assets** | App-specific ML inference appears unavailable as packaged; runtime path should be verified |
| Lint (release module ML) | **CONFIRMED: 3 warnings** | Two 16 KiB native warnings, one idle+charging warning |
| Physical-device VPN test | **NOT RUN** | Android SDK, ADB, emulator and physical device unavailable in the review environment |
| CPU/PSS/battery/DNS latency | **NOT RUN** | These are proposed benchmarks, never inferred from package size |
| Attack/pen-test or code execution from GitHub | **NOT RUN** | Static inspection + existing CI evidence only |

**Method:** `verify_artifacts.py` independently parsed downloaded ZIP artifacts, JUnit XML, ZIP entries and `readelf -lW` output. Keep the JSON evidence with this audit; date-stamp and preserve the originating run IDs.

## 2. Findings: prioritize by risk and user impact

### UA-01 - Public resolver IP host routes capture more than DNS (P0 release blocker until verified)

**Source:** `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt:172-203`, `vpn-engine/src/main/java/com/unblocker/vpn/builder/VpnConfiguration.kt:30-70`.

**Observed:** VPN route builder adds `/32` and `/128` routes for public DNS server addresses including `1.1.1.1` and `8.8.8.8`. `runVpnLoop` is a DNS packet reader, and for unsupported non-DNS packets the fallback path does not forward arbitrary TCP/UDP/HTTPS traffic. `VpnService.Builder.addRoute` matches destination IP prefixes, not TCP/UDP port 53. This could route and drop regular HTTPS traffic bound for one of these IPs.

**Existing contradictory test:** `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt:vpnDoesNotPublishPublicResolverHostRoutes` explicitly asserts these host routes are absent. CI workflow `.github/workflows/android.yml` runs JVM tests but no connected Android instrumentation test.

**Fix:** Keep only synthetic tunnel resolver IP routes in a DNS-only VPN. If direct resolver interception is a mandatory feature, scope a separately specified full forwarding design with packet-level correctness and energy benchmarks. Test with route inspection, intercepted DNS and non-DNS browser/network compatibility. **Runtime failure not reproduced in this review.**

### UA-02 - Logs screen user override is disconnected from active DNS decision (P1)

**Source:** `app/src/main/java/com/unblocker/app/ui/logs/LogViewModel.kt:allowDomain/blockDomain`; `data-store/src/main/java/com/unblocker/data/repository/FilterRepository.kt:whitelistDomain/blacklistDomain`; `app/src/main/java/com/unblocker/app/logic/ContentFilterEngine.kt`; `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt`.

**Observed:** Logs actions mutate Room `WhitelistEntity` / `BlockRuleEntity` and update an `OffHeapRuleEngine`. The production VPN builds `ContentFilterEngine` with `DeviceLearning.allowlist` and `DeviceLearning.blocklist`, which are separate private stores, and calls `DecideBlockingUseCase`. No bridge from Room mutations to those private stores appears on the reviewed call path.

**Risk:** Tapping `Allow` on a blocked domain may not change live DNS behavior. User-set domains are also stored as plaintext in Room, contrasting with promises about opaque manual rule identifiers. **This is a code-path inconsistency, not a reproduced on-device failure.**

**Fix:** Introduce one authoritative user-rule command API backed by the existing private store, route Logs actions through it, preserve and migrate any legacy DB overrides, and test the actual VPN decision before/after updates and restarts.

### UA-03 - Stale tunnel address in device tests and status panel (P1)

**Source:** `LocalPrivacyAndVpnTest.kt:awaitVpnRouting/establishedTunnelBlocksDnsAndStopsCleanly` and `app/src/main/java/com/unblocker/app/ui/components/AgentStatusPanel.kt`.

**Observed:** Tests and dashboard still reference `10.10.0.1`, whereas the active service advertises `10.111.222.2` and `fd00:1:fd00:1:fd00:1:fd00:2`. Existing device tests are stale and may fail or time out for the wrong reason.

**Fix:** Centralize tunnel configuration constants, update tests and panel, assert actual route and DNS behavior in CI.

### UA-04 - Heavy ML native runtime without packaged model (P1 performance / compatibility)

**Source:** `ml-engine/build.gradle.kts:implementation(libs.tflite)`, `TFLiteClassifierImpl.kt:MODEL_ASSET`, `app/build.gradle.kts` module dependency.

**Observed:** APK 19.80 MB (decimal) with 14.69 MB native `.so` files (14.01 MiB), about 74.22% of APK. None of the APK ZIP entries is a `.tflite` model. TFLite implementation falls back to `0f` when loading fails. ARM64 TensorFlow Lite native PT_LOAD alignment is 4096 bytes; lint flags it. Heuristic learning is implemented separately under `app/logic/analysis`.

**Fix:** Trace actual references and compare an app variant without `:ml-engine` / TensorFlow Lite against present behavior. If dormant, remove from production dependency graph and keep future ML experimental. If retained, supply a validated model and use 16 KiB compatible runtime. **Package-size savings are a hypothesis until a new APK is built and measured.**

### UA-05 - Production release can fall back to test signing (P0 release-process blocker)

**Source:** `.github/workflows/release.yml` and `app/build.gradle.kts`.

**Observed:** Production release tag `v2.2.0` used the `releaseTest` build path with the stable test certificate when production secrets were unavailable. CI logs show production release assets passed signature verification against the **test signer**; published release states `Production release 2.2.0`. Workflow accepts this fallback.

**Fix:** Reject production publication when release key or fingerprint missing. Keep explicit test-signed prerelease tags separate. Create an **upgrade compatibility plan**: Android generally rejects APK replacement with a different signing identity; warn existing users if uninstall/reinstall is unavoidable. Preserve public APK digest and signing identity evidence.

### UA-06 - Room uses destructive migration fallback (P1 data preservation)

**Source:** `data-store/src/main/java/com/unblocker/data/di/DataStoreModule.kt` (`fallbackToDestructiveMigration()`).

**Observed:** If schema upgrades lack a migration path, Room may delete user data in managed tables. Current exported v1/v2 schemas exist, but this configuration can silently discard data on future incompatible upgrades.

**Fix:** Add explicit v1->v2 and future migrations with `MigrationTestHelper`, preserving logs and user rules, and remove destructive fallback from production.

### UA-07 - Local logs are not fully bounded; clear/pause semantics are fragile (P1 privacy / P2 performance)

**Source:** `app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt`; `data-store/src/main/java/com/unblocker/data/db/dao/DomainLogDao.kt`.

**Observed:** Raw query events are bounded to 24h/1000, but `domain_stats` has no TTL/row cap. Query logging is enabled by default, `setLoggingPaused()` is process-local and not persisted, and `clearHistory()` clears the queue and DB without a barrier against a concurrent in-flight flush. Each batch makes separate DAO upserts per domain.

**Risk:** Unbounded aggregate DB size, avoidable DB overhead, unexpected logging resumption or stats reappearance after clear. These are static concurrency and retention risks, not measured failures.

**Fix:** Define explicit logs consent and retention, persist pause, enforce cap/TTL, coordinate clear with writer generation/transaction, batch upserts and test races.

### UA-08 - Custom extension updates are unbounded and can replace good data unsafely (P1)

**Source:** `app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt:downloadAndSaveFilter`, `ExtensionUpdateWorker.kt`.

**Observed:** The parser reads an entire HTTP response line-by-line without total-byte or line-length bounds, follows redirects without validating a permitted HTTPS scheme and uses delete+rename rather than a fully atomic rollback. Automatic updates are opt-in, but current worker constraint is only `CONNECTED` and can consume metered data.

**Fix:** HTTPS-only with validated 3xx redirects, max 16 MiB content / 4 KiB line / 250,000 valid rules as initial product limits, timeout and size enforcement **during** reading, temp validation + atomic swap, rollback and clear user error; schedule on unmetered unless user explicitly overrides.

### UA-09 - Active DNS rule lookup allocates repeatedly (P2, optimization hypothesis)

**Source:** `app/src/main/java/com/unblocker/app/logic/rules/RuleSet.kt:match`.

**Observed:** Every match normalizes/splits labels, creates reversed lists, an array of progressively joined domain strings, and often reconstructs matching original domains. This is a plausible GC/CPU pressure point on the VPN hot path. Real cost is **unmeasured**.

**Fix:** First profile allocations and p95 match latency; then evaluate allocation-light key comparison with identical suffix/exact/wildcard and allow-over-block semantics. Avoid mutable global scratch buffers under concurrent DNS queries.

### UA-10 - Background work, UI motion and CI coverage gaps (P2)

**Sources:** `ml-engine/.../WorkScheduler.kt` uses `requiresCharging && requiresDeviceIdle` (Android Lint warning); `app/.../ui/motion`, `AgentStatusPanel` use perpetual pulse effects; `.github/workflows/android.yml` has no emulator stage, and `app/build.gradle.kts` has `abortOnError=false`, `ignoreWarnings=true` and disabled lint checks.

**Fix:** Remove unused worker, defer optional jobs with meaningful constraints, pause motion/recomposition when unneeded, add device tests and make critical warnings fatal. Measure rather than promise CPU/battery savings.

## 3. Release-ready feature opportunities (value vs overhead)

| Feature | User benefit | Resource impact | Prerequisite |
|---|---|---|---|
| Actual one-tap unblock from Logs | Fix broken websites | Near-zero; reuses local rules | UA-02 |
| Explanation of last blocking reason | Less confusion; better trust | Low; reuse existing reason code | UA-02, UA-07 |
| Clear `Limited coverage` warning for strict Private DNS / DoH | Accurate expectations | Very low; no packet inspection needed | UA-01 |
| Local daily summary of counts (not people or ads viewed) | Understand protection | Low if computed from bounded counters | UA-07 |
| `Quiet / Low resource` mode | Less visual/optional workload | Low, bounded | Baseline measurement |
| Custom filter update safety and status | Trust in updates | Download only after opt-in | UA-08 |
| Advanced app exclusions | Avoid app breakage | Medium/high correctness risk | Separate proof-of-concept, defer |

## 4. Reproduction / verification checklist

1. Existing CI evidence: https://github.com/pkgtm2419/un-blocker/actions/runs/41342081066 (260 JVM tests). Existing release evidence: https://github.com/pkgtm2419/un-blocker/actions/runs/41342112852.
2. APK: verify downloaded `releaseTest/ub-blocker-2.2.0.apk` SHA-256 against its release checksum when using the published asset; independent CI archive names do not prove release bit-for-bit identity unless hashes match.
3. Static ZIP inspection: no model assets; eight `.so` files, 14,693,752 native bytes; `readelf -lW` ARM64 native `.so` PT_LOAD alignment `0x1000`.
4. Device: run `./gradlew :app:connectedDebugAndroidTest`; current tests reference stale tunnel IP and may fail. Collect actual route list and app logcat on isolated emulator/device.
5. State: toggle block/allow directly from Logs and query the same domain through a real tunnel, comparing before and after. Then force-stop/relaunch and verify persistence.
6. Bounded extensions: serve intentionally oversized, malformed, redirected and offline responses through controlled local test servers; never target external production hosts.
7. Profiling: capture matched idle/active/burst and two-hour sessions with Android Studio Profiler/Perfetto; compare retained heap, PSS, CPU thread time, battery energy, DNS p50/p95 and errors.

## 5. Constraints and interpretation

No Kotlin app builds or device tests were run in this review environment; GitHub CI evidence is historical and scoped to commit `6fd537e`. Findings derived from source show credible defects or risks but should not be advertised as reproduced crashes. The documentation here is a **plan and audit**, not implemented remediation. The GitHub branch was not modified because the connected GitHub integration returned HTTP 403 on branch creation. These Markdown files are packaged for manual review and committing.
