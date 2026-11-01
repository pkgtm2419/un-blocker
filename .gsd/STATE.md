---
updated: 2026-11-02T00:30:00+05:30
---

# Project State

## Current Position

**Milestone:** Off-Heap Binary Rule Engine, Dual-Stack Sinkhole & Release v2.2.0
**Phase:** Phase 2 - Dual-Stack Split-Tunnel VPN & Checksum-Verified NXDOMAIN Engine
**Status:** ready
**Plan:** Plan 2.1 - 2.3: Refactor DnsPacketEngine.kt and UnblockerVpnService.kt

## Last Action

Completed Phase 1: Implemented `RuleCompiler.kt`, `OffHeapRuleEngine.kt`, and `RuleCompilerTest.kt`. Verified with `./gradlew testDebugUnitTest` (All 120 tasks succeeded).

## Next Steps

1. Refactor `vpn-engine/src/main/java/com/unblocker/vpn/parser/DnsPacketEngine.kt` to support IPv4 & IPv6 parsing and checksum-verified NXDOMAIN synthesis.
2. Refactor `vpn-engine/src/main/java/com/unblocker/vpn/service/UnblockerVpnService.kt` for dual-stack virtual sinkhole, split-tunnel routes, and underlying network tracking.
3. Run Phase 2 verification gate: `./gradlew :vpn-engine:assembleDebug :vpn-engine:testDebugUnitTest`.
4. Commit Phase 2.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Off-Heap 64-bit Index | 64-bit FNV-1a hashes sorted and deduplicated in `rules.bin`, mapped via MappedByteBuffer | 2026-11-02 | Memory footprint dropped from ~280 MB to ~5.5 MB for 686k rules |
| Multi-Syntax Normalization | Support Hosts, Adblock/uBlock (`||domain^$third-party`), and plain domains | 2026-11-02 | Compatibility across 42 active filter lists |
| Zero-Allocation Walk | Compute FNV-1a on label offsets directly without string slicing | 2026-11-02 | Subdomain blocking with 0 GC overhead |
