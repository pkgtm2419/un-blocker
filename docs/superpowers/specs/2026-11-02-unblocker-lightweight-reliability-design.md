# Un-Blocker 2.3: Lightweight Reliability and User Value - Design Specification

**Date:** 2026-11-02  
**Repository:** https://github.com/pkgtm2419/un-blocker  
**Reviewed branch/commit:** `main` / `6fd537e34773e3f799e3685f68a0fbef761eb71c`  
**Status:** Proposed design, pending owner approval. No application code has been changed.  
**Evidence:** [Improvement audit](../../qa/2026-11-02-unblocker-improvement-audit.md); GitHub Actions Android run `41342081066`, release run `41342112852`.

## 1. User problem and intent

Un-Blocker is an Android 8.0+ local DNS filter, built with Kotlin, Compose, Room, WorkManager, Jetpack and Android VpnService. It protects selected DNS traffic locally, offers ad/tracker and optional adult-domain filtering, manual allow/block rules, an extensions catalog, local DNS statistics, and evidence-based heuristic learning. It is not an anonymity VPN, cannot inspect HTTPS content, and cannot guarantee interception of application-specific encrypted DNS.

The next release should make these existing promises reliable and transparent, then reduce installed APK footprint, foreground work, background wakeups, DB growth and interface-rendering cost. Added features must be practical: explain why DNS was blocked, resolve accidental blocking quickly, and identify when filtering is bypassed, all without telemetry or a remote account.

## 2. Design alternatives

| Approach | Benefits | Costs / risks | Decision |
|---|---|---|---|
| A. Lean DNS-only, one authoritative local rule engine, measurement-led tuning | Preserves privacy and low overhead; simpler invariants; fixes user trust | Cannot reliably intercept DoH, DoT, direct IP traffic or enforce per-app rules | **Recommended** |
| B. DNS filter plus optional, fully functioning TFLite classifier | Potentially better novel-domain detection | Requires real model, evaluation corpus, compatible native code, size and energy budgets | Deferred until proven beneficial |
| C. Full-tunnel VPN with UDP/TCP/IP forwarding for all traffic | Can handle direct DNS traffic more completely | Much larger development, security, correctness, battery and networking cost | Out of scope |

**Decision proposed for review:** choose A for the next release. Remove or isolate incomplete ML packaging only after determining its actual runtime role. Communicate bypass limitations instead of intercepting unrelated IP traffic.

## 3. Goals and release acceptance

1. **Connectivity correctness:** The app never captures non-DNS connections merely because their destination IP is used by a DNS resolver. No unintentional Internet outage when protection starts, stops, network switches or VPN consent is withdrawn.
2. **Control correctness:** Every user allow/block action, including the Logs tab, affects the exact production `DecideBlockingUseCase` path immediately and survives process restart without exposing raw rule values contrary to the privacy statement.
3. **Low footprint:** Remove unused platform libraries. Rebuild with 16 KiB page compatible native libraries if any remain. Target a <= 10 MiB universal release APK only after verifying no lost functionality; this is a proposed stretch goal, not a measured result.
4. **Efficient runtime:** Benchmark on low-end and representative devices before tuning. Preserve DNS latency and accuracy while reducing allocations, DB transactions and background work. Track PSS, CPU time, battery energy and GC pauses with real traces.
5. **Safe persistence:** All Room schema upgrades use explicit tested migrations; user rules and logs are not silently discarded. Domain statistics have bounded retention. Clearing history cannot be undone by in-flight flushes.
6. **Filter-list safety:** Untrusted extension URLs and response streams have bounded size, HTTPS validation, safe redirects, atomic replacement and controlled download cadence. A corrupt/oversized update cannot break the active filter set.
7. **Useful transparency:** Provide statuses `Protected`, `Limited coverage`, `Off`, `Starting`, `Error`, with clear reasons. Make a one-tap false-positive correction and lightweight, on-device protection summary available.
8. **Quality gates:** PR and release pipelines enforce core JVM/QA checks and a real instrumented VPN smoke test; production release requires deliberate release signing and verified, non-debuggable artifact provenance.

## 4. Non-goals / constraints

- Do not introduce analytics, crash uploads, accounts, cloud processing, persistent packet payloads, MITM, certificate interception, user browsing history uploads or remote learning.
- Do not claim universal DNS protection against Chrome/Firefox DoH, Android Private DNS strict mode, encrypted protocols or apps that use direct IP connections.
- Keep `minSdk=26`, `targetSdk=35`, Kotlin 2.0.21 and JDK 17 as the starting baseline; changes to compatibility versions require their own verification.
- Preserve current local user rule intent across upgrades. Existing v2.2.0 APK may be test-signed; a production signer change requires an explicit upgrade/migration communication plan.
- Avoid simultaneous replacement of all domain matchers. Preserve precedence: explicit user allow > explicit user block > never-block safety > lists > evidence > adult policy, except where an approved change explicitly requires otherwise.
- Default to **no new recurring jobs**. A user-visible feature must justify its resource consumption and privacy behavior.

## 5. Architectural boundary

**Runtime hot path:** `UnblockerVpnService` -> `DnsPacketUtil`/`TcpDnsResponder` -> `ContentFilterEngine` -> `DecideBlockingUseCase` -> active immutable rules + private user overrides + bounded evidence -> local blocked reply or protected Android-configured upstream DNS.

**Configuration path:** `MainScreen`, `UnblockerScreen`, `LogViewModel`, `ExtensionsViewModel` -> one domain-control interface -> persisted rules or bounded extension set -> atomic visible rule snapshot used by the runtime hot path. Do not keep Room `FilterRepository`/off-heap rules and `DeviceLearning`/RuleSet as independent user-action authorities.

**Observability path:** Non-blocking counters and optional, bounded local DNS statistics -> DAO read flows -> UI, without adding synchronous DB writes to VPN packet processing. Logs must reflect user pause/clear controls across restarts.

**Optional jobs:** Health check on user-selected state or infrequent constraints; filter updates only after opt-in and validated network constraints; no permanently packaged nonfunctional classifier.

## 6. Test and budget policy

- Build baseline and candidate from the **same benchmark conditions**: same physical phone, Android build, Wi-Fi/LTE path, resolver, filter corpus, captured test DNS workload, screen brightness and logging setting. Run each case at least three times; report median and variation, not invented deltas.
- Required scenarios: VPN off, VPN on idle, 1/10/100 queries per second, unique-versus-repeated domains, 10k-domain burst, mobile/Wi-Fi switches, screen on/off, slow/resolver failure, airplane mode, process restart, extended 2-hour DNS workload, first launch and upgrade.
- Proposed initial budget (to ratify after baseline): candidate idle background CPU <= baseline; PSS <= baseline; total battery energy <= baseline for matched DNS workloads; p95 added processing latency no worse than 5% over baseline; zero missed responses on bounded normal workload and zero unintended non-DNS captures. Seek 15-25% gains **where baseline profiler evidence indicates a meaningful bottleneck**.
- Measure filtering quality on a versioned labeled corpus using recall, false-positive rate and bypass tests. No fabricated percent blocked. No release for an apparent energy improvement that materially degrades DNS correctness.
- Device matrix: API 26, 30, 35, and a 16 KiB-page compatible environment; at least one <= 4 GB RAM physical device, one modern physical phone, IPv4-only and dual-stack networks where feasible. Emulators provide correctness coverage, not realistic battery estimates.
- All docs/test evidence use source commit SHA and include actual execution state: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`. Source inspection is not a runtime pass.

## 7. Execution order and owners

| Phase | Plan | Why first | Done when |
|---|---|---|---|
| 1 | [VPN routing](../plans/2026-11-02-vpn-dns-routing-and-compatibility.md) | Prevent connectivity degradation | Device routing and consent tests pass |
| 2 | [Rules and privacy](../plans/2026-11-02-user-rule-consistency-and-privacy.md) | Make user controls real | Log action changes VPN decision; migration protects data |
| 3 | [Resource and ML footprint](../plans/2026-11-02-apk-memory-cpu-and-battery.md) | High-value 14 MiB native dependency footprint | APK diff and repeatable profiler results |
| 4 | [Extensions and storage](../plans/2026-11-02-bounded-filter-updates-and-storage.md) | Prevent unbounded memory/data use | Input-bounds and migration tests pass |
| 5 | [User features](../plans/2026-11-02-protection-transparency-and-user-value.md) | Rebuild user trust and value | UX instrumented acceptance tests pass |
| Parallel after phase 1 | [CI and release](../plans/2026-11-02-device-ci-and-release-quality-gates.md) | Stop regressions reaching users | Required checks enforced; properly signed release |

Treat each phase as independent enough for a dedicated PR with review, while keeping behavior changes behind tests. Approval of this design is **not** approval to ship changes without validation.

## 8. References

- Android VpnService Builder route semantics: https://developer.android.com/reference/android/net/VpnService.Builder
- Android 16 KiB page support: https://developer.android.com/guide/practices/page-sizes
- Android Room migrations: https://developer.android.com/training/data-storage/room/migrating-db-versions
- Android benchmark guidance: https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile
- Android APK splits guidance: https://developer.android.com/build/configure-apk-splits
