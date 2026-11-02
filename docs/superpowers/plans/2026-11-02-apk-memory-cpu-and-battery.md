# APK, Memory, CPU, and Battery Efficiency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce production package size and recurring device resource use while keeping DNS accuracy, privacy, and throughput equal to or better than v2.2.0.

**Architecture:** Establish physical-device baseline measurements, then remove nonfunctional native dependencies and optimize only the hot paths proven costly. Preserve the existing local DNS-only routing and explicit rule precedence. Isolate optional ML or advanced analysis behind a measured build variant, not the default release.

**Tech Stack:** Kotlin, Android Studio Profiler/Perfetto, Android Macrobenchmark, Gradle, JUnit, Android instrumentation, Python APK-analysis tooling.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- Base SHA `6fd537e34773e3f799e3685f68a0fbef761eb71c`, Android API 26+, target 35, JDK 17, Kotlin 2.0.21.
- Artifact facts: v2.2.0 `releaseTest` APK is **19,797,964 bytes**; eight `.so` files total **14,693,752 bytes** (74.22% of APK); packaged `.tflite` assets = 0; ARM64 TFLite PT_LOAD alignment = 0x1000. These are **not** runtime memory or energy measurements.
- Preserve DNS cache bounds, validation, resolver protections, privacy and rule correctness. Never cut CPU by silently dropping legitimate DNS packets.
- Benchmark release-like non-debuggable/profileable builds, not debug with active debugger. Device battery tests must use matched A/B controls.
- Do not claim a package-size, memory or battery improvement until the candidate artifact/device has been measured.

## Review Focus

1. Removing TensorFlow Lite must not disable currently functioning heuristic evidence learning (Task 2).
2. No dangling Hilt injection/WorkManager worker references after optional ML removal (Task 2).
3. Domain matcher exact/suffix/wildcard/allow precedence unchanged after allocation tuning (Task 3).
4. Cache, concurrent writer and resolver queue must not deadlock or drop normal DNS under load (Task 4).
5. Battery-focused background/animation changes must not disable protection itself (Task 5).

---

### Task 1: Capture controlled baseline, not anecdotal numbers

**Files:**
- Create: `benchmark/build.gradle.kts`, `benchmark/src/androidTest/java/com/unblocker/benchmark/StartupBenchmark.kt`, `benchmark/src/androidTest/java/com/unblocker/benchmark/LogListScrollBenchmark.kt`.
- Modify: `settings.gradle.kts` to include `:benchmark` only after dependencies are verified.
- Create: `docs/qa/performance-baseline-v2.2.0.md` and `docs/qa/performance-benchmark-protocol.md`.

**Interfaces:** `StartupBenchmark` measures cold/warm startup with `StartupTimingMetric`; `LogListScrollBenchmark` measures frame timing while navigating real Compose Logs UI. Provide an external controlled DNS client workload generator for latency/throughput; avoid timing live Internet resolvers as if they were app CPU cost.

- [ ] Record current APK hash, SHA, date, devices (>= one <=4 GB RAM and one modern), installed build profile and network settings.
- [ ] Implement startup/jank benchmarks and a repeatable blocked/cached/allowed DNS generator; first run against unmodified v2.2.0.
- [ ] Capture process PSS (`adb shell dumpsys meminfo com.unblocker.app`), Perfetto CPU scheduling, Java/native heap, GC, DNS qps, p50/p95 end-to-end latency, response errors and queue drops for 0/1/10/100 qps, screen on/off and two-hour idle.
- [ ] Measure battery **energy** using Power Profiler/power rails when supported, otherwise controlled repeated battery delta with confidence/variance and control sessions. Do not infer battery from CPU percentages alone.
- [ ] Save raw machine-readable results and median/variation, plus exact scenarios. Commit `test(perf): add reproducible Android performance baseline`.

### Task 2: Remove or isolate TensorFlow Lite if genuinely unused

**Problem:** `ml-engine` packages native TensorFlow Lite for four ABIs; `TFLiteClassifierImpl` expects `domain_classifier.tflite`, but there is no model in the APK. Separate active evidence/heuristic implementation exists in `app/logic/analysis`.

**Files:**
- Review: `ml-engine/src/main/java/com/unblocker/ml/classifier/TFLiteClassifierImpl.kt`, `MlEngineModule.kt`, `BatchDomainAnalysisWorker.kt`, `WorkScheduler.kt`.
- Modify: `app/build.gradle.kts` (production `:ml-engine` dependency), `ml-engine/build.gradle.kts`, and `settings.gradle.kts` only as justified by reference tracing.
- Test: create `app/src/test/java/com/unblocker/app/quality/RuntimeInferenceAvailabilityTest.kt`.
- Create: `scripts/check-release-native-libs.py` (inspects APK ZIP/ELF and model presence).

**Interfaces:** Choose one explicit option after tracing all WorkScheduler/work-manager call sites: (A) `HeuristicOnly` default app with no `tensorflow-lite` dependency and no ML worker scheduling; or (B) a fully validated optional model variant with bundled `.tflite`, measured detection benefit and 16 KiB-compatible native runtime. Option A is preferred absent evidence.

- [ ] Add a failing artifact test for Option A: production APK contains no `libtensorflowlite_jni.so`, and active classifier behavior still comes from app's deterministic heuristic engine.
- [ ] Search actual references to `WorkScheduler`, `DomainClassifier`, WorkManager registration, `:ml-engine`; record which paths are reachable. Do not assume the module is dead only because the asset is absent.
- [ ] Remove runtime dependency from production build (or isolate optional variant) and fix any Hilt/Dagger bindings, worker code and Gradle configuration affected.
- [ ] Run `./gradlew testDebugUnitTest lintRelease assembleReleaseTest`, inspect release ZIP entries, validate signing and compare exact APK sizes. Initial stretch target <= 10 MiB universal APK, subject to remaining dependencies and no regression.
- [ ] Run API 26/35 and 16 KiB-page emulator install/launch tests. Check remaining `.so` libraries with `readelf -lW`, `zipalign -c -P 16 -v 4` (where available) and runtime instrumentation.
- [ ] Commit `perf(apk): exclude unverified native classifier from production`.

### Task 3: Profile and reduce domain-rule matching allocations

**Problem:** `RuleSet.match` uses `split`, list reversal, prefix arrays and repeated `joinToString` per DNS decision. This is likely allocation-heavy; actual cost requires a profiler trace.

**Files:**
- Modify only after profiling: `app/src/main/java/com/unblocker/app/logic/rules/RuleSet.kt`.
- Test: `app/src/test/java/com/unblocker/app/rules/RuleSetDifferentialTest.kt`, `PackedMatcherTest.kt`.
- Create: `app/src/test/java/com/unblocker/app/performance/RuleSetAllocationBenchmarkTest.kt`.

**Interfaces:** Preserve `RuleSet.match(raw: String): DnsRule?`, the immutable UBR2 format, user ALLOW-before-BLOCK behavior, and exact/suffix/wildcard specificity. Implement a thread-safe allocation-light reversed-label comparator only if profiler warrants it; no mutable shared global scratch.

- [ ] Extend differential corpus with 10k labeled rules, mixed case/IDNA, malformed labels, public suffix and wildcard collision cases.
- [ ] Measure allocations/query and p50/p95 match time on baseline; write a failing regression assertion for **semantic correctness**, not a flaky nanoTime threshold.
- [ ] Optimize matcher implementation in a small isolated patch; use `StringBuilder`/bounded local buffer or validated `CharSequence` comparison without repeated prefix lists.
- [ ] Rerun differential/JVM and actual Android DNS traffic tests, then compare profiler allocations/GC CPU. Revert if correctness regresses or gain is within noise.
- [ ] Commit `perf(rules): reduce measured matcher hot-path allocations`.

### Task 4: Tune forwarding, cache and log batching only after traces

**Files:**
- Review/possibly modify: `app/src/main/java/com/unblocker/app/services/TunnelRun.kt`, `DnsRequestCoalescer.kt`, `TunResponseWriter.kt`, `DnsUpstreamClient.kt`, `app/src/main/java/com/unblocker/app/logic/dns/DnsCache.kt`, `app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt`.
- Test: `app/src/test/java/com/unblocker/app/services/DnsRequestCoalescerTest.kt`, `TunResponseWriterTest.kt`, `DnsUpstreamClientTest.kt`, `app/src/test/java/com/unblocker/app/dns/DnsCacheTest.kt`.

**Interfaces:** Keep socket `protect()` behavior and resolver epoch guards. Retain queue backpressure counters and session-owner invariants. Changes to executor thread count (currently four fixed forwarding threads), cache size (currently 1000 entries), log batch size (200), or queue size (10k logs) must be driven by measured contention/pressure.

- [ ] Benchmark bursty identical and unique DNS workloads; record coalesced requests, `TUN_WRITE_ERROR` drops, CPU/RAM, p95 latency and number of live threads.
- [ ] Add repeatable tests for 10k bounded queries, upstream timeout, repeated network switches, packet-owner teardown and empty resolver list.
- [ ] Tune a single variable at a time, rerunning the same fixed workload; verify no deadlocks, stale responses or increased SERVFAIL.
- [ ] Document before/after with percent changes and error rates, including no-change or rollback when data does not justify tuning.
- [ ] Commit `perf(vpn): tune verified resource bottlenecks` only for changes demonstrated beneficial.

### Task 5: Eliminate optional wakeups, costly UI motion and unsupported ML schedules

**Files:**
- Modify: `ml-engine/src/main/java/com/unblocker/ml/worker/WorkScheduler.kt` only if retained.
- Review: `app/src/main/java/com/unblocker/app/services/HealthCheckWorker.kt`, `ExtensionUpdateWorker.kt`, `QuickStartManager.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/motion/MotionPrimitives.kt`, `ui/components/AgentStatusPanel.kt`, `ui/screens/UnblockerScreen.kt` if benchmark identifies continuous rendering cost.
- Test: create `app/src/test/java/com/unblocker/app/quality/OptionalWorkPolicyTest.kt`.

**Interfaces:** Define an optional `LowResourceUiPolicy` (system reduced-motion / app user preference) affecting only visual effects and noncritical background analysis. The DNS service remains active; never gate security on UI lifecycle.

- [ ] Add test that unused ML batch jobs are not scheduled by the default production build; if retained, remove incompatible `charging + idle` constraint combination or document device-specific supported schedule.
- [ ] Add test that opt-in extension updates run on unmetered connectivity by default, with explicit manual override; cancel on user disable.
- [ ] Implement motion pause or static substitution when user opts for reduced motion, app is not visible or system animation scale is zero.
- [ ] Re-profile screen jank/CPU, periodic jobs and system wakeups. Preserve accessible state transitions and correct VPN protection throughout.
- [ ] Commit `perf(background): minimize optional work and motion`.

## Exit gates

- Package comparison from actual signed APK artifacts; model/native compatibility verified, no phantom inference claims.
- Identical DNS correctness and filter-rule decisions before and after performance changes.
- CPU, PSS, energy, GC and p95 latency measured on physical devices with controlled A/B conditions; candidates must not regress critical metrics. No estimate masquerades as measured savings.
- API 26, 30, 35 and 16 KiB-page smoke validation; release-like build install and run verified.
