# SPEC.md — Project Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: No code may be written until this spec is marked `FINALIZED`.

## Vision
Stabilize, optimize, and harden `un-blocker` according to the deep technical audit in `docs/deep-research-report.md`. This transforms the local DNS blocker from a prototype prone to silent packet drops and allocation churn into a highly reliable, observable, memory-efficient, and privacy-preserving networking service, culminating in a new release build (`v2.1.6`).

## Goals
1. **Fix Forwarding Saturation & Drop Defect** — Eliminate silent DNS drops when the 4-worker/64-capacity executor is full; respond with deterministic `SERVFAIL` (preserving transaction ID and question section) and track rejection metrics.
2. **Privacy-Safe Structured Diagnostics** — Stop discarding networking exceptions; record structured diagnostics (failure code, exception class, queue depth, resolver count) with zero domain or PII leakage.
3. **Single TUN Response Writer** — Replace multithreaded `writeLock` contention on `FileOutputStream` with a dedicated single-writer coroutine/queue.
4. **Hot-Path Allocation Reduction** — Parse incoming TUN packets directly from the pooled buffer without unconditional `packetBuffer.copyOf(length)`; construct compact immutable DTOs only for asynchronous forwarding tasks.
5. **Remove `largeHeap="true"`** — Eliminate `android:largeHeap="true"` from the manifest and ensure the app runs comfortably within standard Android process memory budgets.
6. **Domain History & Privacy Hardening** — Make raw domain history opt-in; record aggregate metrics by default; reduce retention cap; handle log database exceptions with health state instead of silent failure.
7. **Rule Correctness & IDNA Canonicalization** — Support Unicode internationalized domain names (IDN/Punycode via `java.net.IDN.toASCII`) at manual entry and import boundaries before canonical normalization.
8. **Release & Packaging** — Version bump to `2.1.6` (versionCode 24), run comprehensive test suites, and produce a verified release APK.

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
- [ ] Forwarding saturation returns `SERVFAIL` and increments diagnostic counters.
- [ ] Exceptions in TUN and response paths captured with structured failure codes.
- [ ] Single TUN writer manages all output packets without `writeLock`.
- [ ] No full-packet copies in the hot loop for blocked and cache-hit queries.
- [ ] `android:largeHeap="true"` removed from `AndroidManifest.xml`.
- [ ] Privacy mode enabled by default in `QueryLogSink` with aggregate statistics.
- [ ] IDNA manual domain entry canonicalizes internationalized domains.
- [ ] 100% of unit tests pass with zero regressions.
- [ ] Release APK built and verified.
- [ ] Changes committed and pushed to git with atomic conventional commits.
