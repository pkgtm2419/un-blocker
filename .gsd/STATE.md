---
updated: 2026-10-30T20:56:00+05:30
---

# Project State

## Current Position

**Milestone:** Performance Optimization, Coalescing & Release Hardening v2.1.7
**Phase:** 10 - CI/CD Release Policy Hardening & Fuzz Testing
**Status:** executing
**Plan:** Plan 10.1 - 10.3: Add malformed DNS packet fuzz tests, harden release policy and CI signing fallback

## Last Action

Completed Phase 9: Streamlined `DnsCache` eviction to O(1), cut 50% byte-array allocations in `toForwardingRequest`, and verified all 116 unit test tasks pass with zero regressions.

## Next Steps

1. Create `DnsRequestCoalescer.kt` in `com.unblocker.app.services`.
2. Integrate coalescer into `UnblockerVpnService.kt` during cache-miss handling.
3. Wire `VpnDiagnostics.coalescedQuery()`.
4. Create comprehensive tests in `DnsRequestCoalescerTest.kt`.
5. Run unit tests and commit Phase 8.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Query Coalescing Key | `CoalesceKey(domain, queryType, queryClass, isIpv6, epoch)` | 2026-10-30 | Deduplication correctness across resolver network switches |
| Waiter Fan-Out | Each attached query receives custom-wrapped response with its own txId and IP/UDP headers | 2026-10-30 | RFC DNS transaction integrity |
| Bounded Waiters | 32 max waiters per key to prevent unbounded memory on adversarial query loops | 2026-10-30 | Memory bounding |

## Blockers

None.

## Concerns

None.
