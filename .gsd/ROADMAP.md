---
milestone: Stabilization and Release v2.1.6
version: 2.1.6
updated: 2026-10-29T23:51:00+05:30
---

# Roadmap

> **Current Phase:** Phase 1 - Reliability and Observability
> **Status:** executing

## Must-Haves (from SPEC)
- [ ] Forwarding saturation generates deterministic SERVFAIL response instead of dropping queries
- [ ] VpnDiagnostics captures failure codes, exception class, queue depth, and metrics with 0 domain/PII leakage
- [ ] Single TUN response writer queue replaces global writeLock contention
- [ ] Hot-path packet allocations removed for blocked and cache-hit flows
- [ ] `android:largeHeap="true"` removed from AndroidManifest.xml
- [ ] Local query logging defaults to privacy mode (aggregate counters only) with opt-in history
- [ ] IDNA Punycode conversion added at rule-entry / UI manual import boundaries
- [ ] All unit tests pass with zero regressions
- [ ] Release APK built, verified, and committed

---

## Phases

### Phase 1: Reliability and Observability
**Status:** ✅ Complete
**Objective:** Add `SERVFAIL` generation on executor rejection and implement `VpnDiagnostics` for privacy-safe failure tracking.
**Plans:**
- [x] Plan 1.1: Implement `DnsPacketUtil.buildServfailDnsResponsePacket` & `buildServfailWireResponse`
- [x] Plan 1.2: Implement `VpnDiagnostics` and integrate with `UnblockerVpnService` rejection & exception paths
- [x] Plan 1.3: Add unit tests for SERVFAIL and diagnostics

### Phase 2: Single TUN Response Writer
**Status:** ✅ Complete
**Objective:** Introduce a dedicated single-writer response queue to serialize all TUN writes without `writeLock` contention.
**Plans:**
- [x] Plan 2.1: Implement `TunResponseWriter` with bounded queue and run-liveness verification
- [x] Plan 2.2: Migrate UDP, TCP, blocked, cached, and forwarded paths to the response writer
- [x] Plan 2.3: Verify concurrency and lifecycle tests

### Phase 3: Hot-Path Allocation Reduction
**Status:** 🔄 In Progress
**Objective:** Remove unconditional `packetBuffer.copyOf(length)` in the reader loop; introduce lightweight forwarding DTO for async requests.
**Plans:**
- [ ] Plan 3.1: Adapt `DnsPacketUtil.parseIpPacket` and TCP responder to parse directly in the reusable buffer
- [ ] Plan 3.2: Create compact `ForwardingDnsRequest` containing only required payload/metadata for upstream resolution
- [ ] Plan 3.3: Verify buffer isolation and regression tests

### Phase 4: Memory Baseline & largeHeap Removal
**Status:** ⬜ Not Started
**Objective:** Remove `android:largeHeap="true"` from manifest and verify execution under standard memory limits.
**Plans:**
- [ ] Plan 4.1: Remove `android:largeHeap="true"` from `app/src/main/AndroidManifest.xml`
- [ ] Plan 4.2: Verify build and unit test suite

### Phase 5: Domain and History Privacy Hardening
**Status:** ⬜ Not Started
**Objective:** Make raw domain storage opt-in; record aggregate counters by default; handle database errors cleanly.
**Plans:**
- [ ] Plan 5.1: Add privacy-first history mode and aggregate counters in `QueryLogSink`
- [ ] Plan 5.2: Safe error handling and health state for Room DB writes
- [ ] Plan 5.3: Add unit tests for history modes and privacy invariants

### Phase 6: Rule Correctness & IDNA Canonicalization
**Status:** ⬜ Not Started
**Objective:** Support IDN/Punycode at manual domain entry and rule import boundaries; verify rule precedence.
**Plans:**
- [ ] Plan 6.1: Add `DomainName.normalizeUserEntry` with `java.net.IDN.toASCII`
- [ ] Plan 6.2: Integrate into custom rule additions and verify precedence test cases
- [ ] Plan 6.3: Add tests for adult content boundary cases and internationalized domains

### Phase 7: Version Bump, Release Build & Packaging
**Status:** ⬜ Not Started
**Objective:** Update version metadata to 2.1.6 (code 24), run full verification, assemble release APK, and push commits.
**Plans:**
- [ ] Plan 7.1: Bump version to 2.1.6 (versionCode 24) in `app/build.gradle.kts`
- [ ] Plan 7.2: Run complete test suite (`testDebugUnitTest` and lint)
- [ ] Plan 7.3: Assemble release APK and verify output artifacts
- [ ] Plan 7.4: Git push and release summary
