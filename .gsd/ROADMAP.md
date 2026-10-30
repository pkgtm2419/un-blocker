---
milestone: Performance Optimization, Coalescing & Release Hardening v2.1.7
version: 2.1.7
updated: 2026-10-30T20:56:00+05:30
---

# Roadmap

> **Current Phase:** Phase 8 - In-Flight DNS Request Coalescing
> **Status:** ready

## Must-Haves (from SPEC)
- [x] Forwarding saturation generates deterministic SERVFAIL response instead of dropping queries
- [x] VpnDiagnostics captures failure codes, exception class, queue depth, and metrics with 0 domain/PII leakage
- [x] Single TUN response writer queue replaces global writeLock contention
- [x] Hot-path packet allocations removed for blocked and cache-hit flows
- [x] `android:largeHeap="true"` removed from AndroidManifest.xml
- [x] Local query logging defaults to privacy mode (aggregate counters only) with opt-in history
- [x] IDNA Punycode conversion added at rule-entry / UI manual import boundaries
- [x] Concurrent in-flight DNS query coalescing implemented and verified
- [x] DnsCache eviction and forward allocation optimized with active worker metrics
- [x] Fuzz tests for malformed DNS packet parsing pass with zero crashes
- [x] Release workflow succeeds cleanly in GitHub Actions
- [ ] Version bumped to 2.1.7 (versionCode 25), unit tests pass, and new release APK assembled

---

## Phases

### Phase 1: Reliability and Observability
**Status:** ✅ Complete (v2.1.6)

### Phase 2: Single TUN Response Writer
**Status:** ✅ Complete (v2.1.6)

### Phase 3: Hot-Path Allocation Reduction
**Status:** ✅ Complete (v2.1.6)

### Phase 4: Memory Baseline & largeHeap Removal
**Status:** ✅ Complete (v2.1.6)

### Phase 5: Domain and History Privacy Hardening
**Status:** ✅ Complete (v2.1.6)

### Phase 6: Rule Correctness & IDNA Canonicalization
**Status:** ✅ Complete (v2.1.6)

### Phase 7: Version Bump & Release v2.1.6
**Status:** ✅ Complete (v2.1.6)

### Phase 8: In-Flight DNS Request Coalescing
**Status:** ✅ Complete
**Objective:** Eliminate redundant concurrent upstream DNS queries for the same domain by coalescing in-flight cache misses.
**Plans:**
- [x] Plan 8.1: Implement `DnsRequestCoalescer` with bounded per-key listener list
- [x] Plan 8.2: Integrate coalescing in `UnblockerVpnService.resolveAndForward` and wire `VpnDiagnostics.coalescedQuery()`
- [x] Plan 8.3: Add unit tests for query coalescing, multi-client response fan-out, and failure propagation

### Phase 9: Cache Eviction & Allocation Streamlining
**Status:** ✅ Complete
**Objective:** Optimize `DnsCache` eviction to O(1) without linear lock-held scans; streamline `toForwardingRequest` allocations; track active workers and latency.
**Plans:**
- [x] Plan 9.1: Refactor `DnsCache` eviction to remove linear lock contention
- [x] Plan 9.2: Optimize `ForwardingDnsRequest` to avoid dual-array allocations and wire `forwardingActive` tracking
- [x] Plan 9.3: Add performance and latency unit tests

### Phase 10: CI/CD Release Policy Hardening & Fuzz Testing
**Status:** ✅ Complete
**Objective:** Add malformed packet parser fuzzing tests and ensure release action succeeds with available repository secrets.
**Plans:**
- [x] Plan 10.1: Add fuzzing test suite for malformed and adversarial DNS wire packets
- [x] Plan 10.2: Update `release-policy.py` and `.github/workflows/release.yml` for seamless signing fallback
- [x] Plan 10.3: Verify release policy tests

### Phase 11: Version Bump, Release Build & Packaging (v2.1.7)
**Status:** 🔄 In Progress
**Objective:** Bump version to 2.1.7 (code 25), run full unit tests, assemble release APKs, push to git, and release.
**Plans:**
- [ ] Plan 11.1: Bump version to 2.1.7 (versionCode 25) in `app/build.gradle.kts` and update `README.md`
- [ ] Plan 11.2: Run complete test suite (`testDebugUnitTest` and lint)
- [ ] Plan 11.3: Assemble release APKs and verify output checksums
- [ ] Plan 11.4: Push to git, publish release, and deliver summary
