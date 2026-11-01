# SPEC.md — Project Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: No code may be written until this spec is marked `FINALIZED`.

## Vision
Deliver `v2.2.0` of `un-blocker`: Eliminate the ~280 MB JVM heap memory bottleneck across 42 active filters (~686,200 rules) via an off-heap 64-bit binary rule engine (`rules.bin`), fix dual-stack ad-blocking across Wi-Fi, 5G, and LTE via split-tunnel routing and checksum-verified NXDOMAIN synthesis, buffer database logging, stabilize R8 minification/CodeQL/Release CI pipelines, and publish release `v2.2.0`.

## Goals
1. **Off-Heap Binary Rule Engine & Multi-Syntax Compiler (Phase 1)** — Compile Hosts (`0.0.0.0`), Adblock/uBlock (`||domain^$third-party`), and plain domains into sorted, deduplicated 64-bit FNV-1a hashes in `rules.bin`. Map off-heap via `MappedByteBuffer` with allocation-free $O(\log_2 N)$ binary search and subdomain walk.
2. **Dual-Stack Split-Tunnel VPN & Checksum-Verified NXDOMAIN Engine (Phase 2)** — Support IPv4 and IPv6 DNS query parsing, valid IPv4 header checksums and RFC 2460 IPv6 UDP checksums, transparent NXDOMAIN synthesis (`0x8183`), and dual-stack DNS server routing (`10.111.222.1/24`, `fd00:1:fd00:1:fd00:1:fd00:1/128`).
3. **Batched Database Logging & Deferred On-Device Learning (Phase 3)** — Non-blocking `Channel<DnsLogEntity>` with batch insertion to prevent SQLite lock contention and UI frame drops during high-frequency network bursts.
4. **R8 Keep Rules, Lint Stabilization & GitHub Actions CI/CD Repair (Phase 4)** — Add explicit keep rules for Room, JNI, and TFLite; configure lint warnings; fix GitHub Actions checkout/Java 17 setup, permissions, and release publication; bump version to `2.2.0` (`versionCode = 27`).
5. **Release v2.2.0 Packaging & Verification (Phase 5)** — Run full verification gates, assemble release APKs, commit with atomic commits, tag `v2.2.0`, push to GitHub, and verify automated release.

## Constraints
- Target Android SDK 35, minimum SDK 26.
- Strict privacy: Zero PII / domain leakage in logs.
- Memory: Maximum ~5.5 MB memory footprint for 686k rules off-heap.
- Zero placeholders: Complete, compilable production code.
- Phase-gated execution: Stop and fix on any failure before moving to the next phase.

## Success Criteria
- [x] SPEC finalized and roadmap planned.
- [ ] Phase 1: `RuleCompiler` and `OffHeapRuleEngine` pass `RuleCompilerTest` with zero string allocations on hot path.
- [ ] Phase 2: Dual-stack IPv4/IPv6 packet parser and checksum synthesis pass `:vpn-engine:testDebugUnitTest`.
- [ ] Phase 3: Buffered log repository and deferred ML learning pass `assembleDebug` and `testDebugUnitTest`.
- [ ] Phase 4: Proguard keep rules, lint configuration, and CI workflows updated; version bumped to `2.2.0`.
- [ ] Phase 5: Complete test suite passes, git tagged `v2.2.0`, pushed, and GitHub Release verified.
