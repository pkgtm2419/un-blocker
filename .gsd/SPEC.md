# SPEC.md — Project Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: No code may be written until this spec is marked `FINALIZED`.

## Vision
Stabilize, optimize, and harden `un-blocker` according to the deep technical audit in `docs/deep-research-report.md`. This transforms the local DNS blocker from a prototype prone to silent packet drops and allocation churn into a highly reliable, observable, memory-efficient, and privacy-preserving networking service, culminating in version `v2.1.7`.

## Goals
1. **Fix Forwarding Saturation & Drop Defect** — Eliminate silent DNS drops when the 4-worker/64-capacity executor is full; respond with deterministic `SERVFAIL` (preserving transaction ID and question section) and track rejection metrics. *(Completed in v2.1.6)*
2. **Privacy-Safe Structured Diagnostics** — Stop discarding networking exceptions; record structured diagnostics (failure code, exception class, queue depth, resolver count) with zero domain or PII leakage. *(Completed in v2.1.6)*
3. **Single TUN Response Writer** — Replace multithreaded `writeLock` contention on `FileOutputStream` with a dedicated single-writer coroutine/queue. *(Completed in v2.1.6)*
4. **Hot-Path Allocation Reduction** — Parse incoming TUN packets directly from the pooled buffer without unconditional `packetBuffer.copyOf(length)`; construct compact immutable DTOs only for asynchronous forwarding tasks. *(Completed in v2.1.6)*
5. **Remove `largeHeap="true"`** — Eliminate `android:largeHeap="true"` from the manifest and ensure the app runs comfortably within standard Android process memory budgets. *(Completed in v2.1.6)*
6. **Domain History & Privacy Hardening** — Make raw domain history opt-in; record aggregate metrics by default; reduce retention cap; handle log database exceptions with health state instead of silent failure. *(Completed in v2.1.6)*
7. **Rule Correctness & IDNA Canonicalization** — Support Unicode internationalized domain names (IDN/Punycode via `java.net.IDN.toASCII`) at manual entry and import boundaries before canonical normalization. *(Completed in v2.1.6)*
8. **In-Flight Request Coalescing (Deduplication)** — Track concurrent in-flight upstream DNS queries by `(domain, queryType, queryClass, isIpv6, epoch)` so simultaneous cache misses share one upstream query rather than congesting the executor and mobile radio.
9. **DnsCache & Allocation Optimization** — Optimize `DnsCache` eviction to prevent linear iteration under lock; streamline `ForwardingDnsRequest` to avoid dual-array allocations; wire live `forwardingActive` and upstream latency diagnostics.
10. **CI/CD Release Automation & Fuzz Testing** — Add fuzz test suite for malformed DNS packets; harden `release-policy.py` and GitHub Actions `release.yml` to support building and publishing releases cleanly with available repository keys.
11. **Release & Packaging** — Bump version to `2.1.7` (versionCode 25), verify 100% test pass, assemble release APKs, and update documentation.

## Non-Goals (Out of Scope)
- General packet/anonymity VPN implementation (this is strictly a local DNS filtering service).
- URL/HTTPS path-level inspection (impossible without MITM certificates; DNS filter only operates on domain names).
- Desktop or iOS ports.
- External analytics or cloud reporting (all logging and diagnostics remain strictly local and on-device).

## Constraints
- Target Android SDK 35, minimum SDK 26.
- Strict privacy: No user hostnames, URLs, or client IPs in diagnostics or exception logs.
- Memory & Performance: Non-blocking reader loop; bounded queues; bounded buffers.
- Deterministic rule precedence: User Allow > User Block > Never Block > Shipped Rules > Adaptive > Adult.

## Success Criteria
- [x] SPEC finalized and roadmap planned.
- [x] Forwarding saturation returns `SERVFAIL` and increments diagnostic counters.
- [x] Exceptions in TUN and response paths captured with structured failure codes.
- [x] Single TUN writer manages all output packets without `writeLock`.
- [x] No full-packet copies in the hot loop for blocked and cache-hit queries.
- [x] `android:largeHeap="true"` removed from `AndroidManifest.xml`.
- [x] Privacy mode enabled by default in `QueryLogSink` with aggregate statistics.
- [x] IDNA manual domain entry canonicalizes internationalized domains.
- [ ] Concurrent in-flight DNS query coalescing implemented and verified.
- [ ] DnsCache eviction and forward allocation optimized with active worker metrics.
- [ ] Fuzz tests for malformed DNS packet parsing pass with zero crashes.
- [ ] Release workflow succeeds cleanly in GitHub Actions.
- [ ] Version bumped to 2.1.7 (versionCode 25), unit tests pass, and new release APK assembled.
