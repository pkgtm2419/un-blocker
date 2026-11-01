---
milestone: Off-Heap Binary Rule Engine, Dual-Stack Sinkhole & Release v2.2.0
version: 2.2.0
updated: 2026-11-02T00:30:00+05:30
---

# Roadmap

> **Current Phase:** Phase 4 - R8 Keep Rules, Lint Stabilization & GitHub Actions CI/CD Repair
> **Status:** ready

## Must-Haves (from SPEC)
- [x] 64-bit FNV-1a binary index (`rules.bin`) mapped off-heap with $O(\log_2 N)$ binary search (Phase 1)
- [x] Multi-syntax rule compiler normalizing Hosts, Adblock/uBlock, and plain domains (Phase 1)
- [x] Dual-stack IPv4/IPv6 DNS parsing and checksum-verified NXDOMAIN synthesis in VPN engine (Phase 2)
- [x] Split-tunnel routing for DNS addresses with underlying physical network auto-tracking (Phase 2)
- [x] Non-blocking buffered log channel (`Channel<DnsLogEntity>`) with batched DB insertion (Phase 3)
- [x] Deferred on-device ML rule compilation and removal of high-memory bottleneck warnings (Phase 3)
- [ ] Proguard keep rules for Room/JNI/TFLite and lint configuration (Phase 4)
- [ ] GitHub Actions workflow repairs (`android-checks.yml`, `codeql.yml`, `release.yml`) (Phase 4)
- [ ] Version bumped to 2.2.0 (versionCode 27), test verification gates, and release packaging (Phase 5)

---

## Phases

### Phase 1: Off-Heap Binary Rule Engine & Multi-Syntax Compiler
**Status:** ✅ Completed
**Objective:** Eliminate the ~280 MB JVM heap spike and GC freezes caused by holding ~686,200 domain strings in memory. Normalize Hosts (`0.0.0.0`), Adblock/uBlock (`||domain^$third-party`), and plain-domain syntaxes into a sorted, deduplicated 64-bit FNV-1a binary index (`rules.bin`) mapped off-heap via `MappedByteBuffer`.
**Plans:**
- [x] Plan 1.1: Implement `RuleCompiler.kt` in `data-store` with multi-syntax parser, FNV-1a 64-bit hash, and binary file compiler
- [x] Plan 1.2: Implement `OffHeapRuleEngine.kt` in `data-store` with off-heap MappedByteBuffer, atomic hot-reload, and zero-allocation binary search
- [x] Plan 1.3: Add unit tests in `RuleCompilerTest.kt` and run Phase 1 verification gate (`./gradlew testDebugUnitTest`)

### Phase 2: Dual-Stack Split-Tunnel VPN & Checksum-Verified NXDOMAIN Engine
**Status:** ✅ Completed
**Objective:** Fix dual-stack ad-blocking across Wi-Fi, 5G, and LTE via split-tunnel routing, valid IPv4 header checksums, RFC 2460 IPv6 UDP checksums, and transparent NXDOMAIN synthesis.
**Plans:**
- [x] Plan 2.1: Refactor `DnsPacketEngine.kt` with IPv4/IPv6 parsing, checksum validation, and NXDOMAIN synthesis
- [x] Plan 2.2: Refactor `UnblockerVpnService.kt` with dual-stack virtual sinkhole, DNS-only split-tunnel routes, and underlying network tracking
- [x] Plan 2.3: Run Phase 2 verification gate (`./gradlew :vpn-engine:assembleDebug :vpn-engine:testDebugUnitTest`)

### Phase 3: Batched Database Logging & Deferred On-Device Learning
**Status:** ✅ Completed
**Objective:** Prevent SQLite lock contention and UI frame drops during heavy network bursts while maintaining the on-device learning pipeline.
**Plans:**
- [x] Plan 3.1: Implement buffered log channel in `LogRepository.kt` with batch insertion
- [x] Plan 3.2: Update `BatchDomainAnalysisWorker.kt` to trigger off-heap rule recompilation and clean up manifest memory flags
- [x] Plan 3.3: Run Phase 3 verification gate (`./gradlew assembleDebug testDebugUnitTest`)

### Phase 4: R8 Keep Rules, Lint Stabilization & GitHub Actions CI/CD Repair
**Status:** ⏳ Pending
**Objective:** Fix all failures in CI workflows, add explicit keep rules for Room/JNI/TFLite, configure lint warnings, and bump version to 2.2.0.
**Plans:**
- [ ] Plan 4.1: Update `app/proguard-rules.pro` with Room, JNI, TFLite, and Coroutines keep rules
- [ ] Plan 4.2: Update `app/build.gradle.kts` with `versionCode = 27`, `versionName = "2.2.0"`, and lint options
- [ ] Plan 4.3: Repair GitHub Actions workflows (`.github/workflows/*.yml`) for Java 17, permissions, and robust release publishing
- [ ] Plan 4.4: Run Phase 4 verification gate (`./gradlew testDebugUnitTest lintDebug assembleRelease`)

### Phase 5: Repository Hygiene, Commit, Tag & Push Release v2.2.0
**Status:** ⏳ Pending
**Objective:** Verify working tree cleanliness, commit with atomic commits, tag `v2.2.0`, push to GitHub, and verify automated release publishing.
**Plans:**
- [ ] Plan 5.1: Pre-push hygiene and verification gate
- [ ] Plan 5.2: Git commit, tag, and push release `v2.2.0`
- [ ] Plan 5.3: Verify GitHub Actions run and release assets
