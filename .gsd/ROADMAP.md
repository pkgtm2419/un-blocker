---
milestone: Security Hardening, Direct-DNS Interception & Release v2.1.8
version: 2.1.8
updated: 2026-10-30T23:15:00+05:30
---

# Roadmap

> **Current Phase:** Phase 12 - DNS Upstream TxID Randomization & Direct Public DNS Route Trapping
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
- [ ] Upstream DNS queries randomized with cryptographically secure 16-bit TxIDs (SEC-02)
- [ ] Direct public DNS IP destinations trapped and filtered via TUN routing (SEC-01)
- [ ] TCP DNS connection pool implements LRU eviction without dropping new requests (SEC-03)
- [ ] TFLite model loader closes file streams safely (SEC-05)
- [ ] Version bumped to 2.1.8 (versionCode 26), unit tests pass, and new release APK assembled

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
**Status:** ✅ Complete (v2.1.7)

### Phase 9: Cache Eviction & Allocation Streamlining
**Status:** ✅ Complete (v2.1.7)

### Phase 10: CI/CD Release Policy Hardening & Fuzz Testing
**Status:** ✅ Complete (v2.1.7)

### Phase 11: Version Bump, Release Build & Packaging (v2.1.7)
**Status:** ✅ Complete (v2.1.7)

### Phase 12: DNS Upstream TxID Randomization & Direct Public DNS Route Trapping
**Status:** ✅ Complete
**Objective:** Decouple client transaction IDs from outbound network queries using cryptographic randomization; route major public DNS resolvers through TUN to trap evasive direct-socket traffic.
**Plans:**
- [x] Plan 12.1: Implement TxID rewriting and response re-mapping in `DnsUpstreamClient`
- [x] Plan 12.2: Expand `VpnConfiguration` routes to trap public DNS IP addresses (`8.8.8.8`, `1.1.1.1`, etc.)
- [x] Plan 12.3: Update `UnblockerVpnService` to process DNS packets directed to public resolver IPs
- [x] Plan 12.4: Add unit tests for TxID randomization and route trapping

### Phase 13: TCP DNS Connection LRU Eviction & TFLite Stream Cleanup
**Status:** 🔄 In Progress
**Objective:** Replace connection-cap rejection with LRU eviction in `TcpDnsResponder` to prevent connection-exhaustion DoS; fix `FileInputStream` resource leak in `TFLiteClassifierImpl`.
**Plans:**
- [ ] Plan 13.1: Implement LRU connection eviction in `TcpDnsResponder`
- [ ] Plan 13.2: Safely close `FileInputStream` in `TFLiteClassifierImpl`
- [ ] Plan 13.3: Add unit tests for TCP connection eviction under saturation

### Phase 14: Version Bump, Verification Suite & Release v2.1.8
**Status:** ⏳ Pending
**Objective:** Bump version to 2.1.8 (code 26), run complete test suite, assemble release APKs, push to git, and publish release.
**Plans:**
- [ ] Plan 14.1: Bump version to 2.1.8 (versionCode 26) in `app/build.gradle.kts` and update `README.md`
- [ ] Plan 14.2: Execute test suite (`testDebugUnitTest` and tooling tests)
- [ ] Plan 14.3: Assemble release APKs and verify signatures
- [ ] Plan 14.4: Push to git, publish release, and deliver final report
