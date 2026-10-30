---
updated: 2026-10-30T23:15:00+05:30
---

# Project State

## Current Position

**Milestone:** Security Hardening, Direct-DNS Interception & Release v2.1.8
**Phase:** 13 - TCP DNS Connection LRU Eviction & TFLite Stream Cleanup
**Status:** executing
**Plan:** Plan 13.1 - 13.3: Implement LRU connection eviction, safe stream cleanup, and unit tests

## Last Action

Completed Phase 12: Implemented cryptographically secure upstream DNS transaction ID randomization (RFC 5452 anti-spoofing) with client TxID restoration in `DnsUpstreamClient`, trapped public DNS IP routes (`8.8.8.8`, `1.1.1.1`, `9.9.9.9`, etc.) into TUN routing in `VpnConfiguration` and `UnblockerVpnService`, updated `TcpDnsResponder` to accept trapped destinations, verified client response wrapping in `DnsPacketUtil`, and added tests in `DnsUpstreamClientSecurityTest` (116/116 test tasks passed).

## Next Steps

1. Implement LRU connection eviction in `TcpDnsResponder` when connection limit (32) is reached.
2. Safely close `FileInputStream` and asset file descriptor in `TFLiteClassifierImpl.setupInterpreter`.
3. Add unit test for LRU connection eviction under saturation in `TcpDnsResponderTest`.
4. Commit Phase 13.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Upstream TxID Randomization | Generate independent `SecureRandom` 16-bit TxID per upstream query, rewrite response back to client's TxID | 2026-10-30 | Anti-spoofing / Kaminsky protection (RFC 5452) |
| Public DNS Trapping | Route top public DNS IPs (`8.8.8.8`, `1.1.1.1`, `9.9.9.9`, `208.67.222.222`, etc.) through TUN | 2026-10-30 | Prevents evasive ad/tracking direct-IP bypass |
| TCP Connection LRU | Evict least-recently-active connection when connection pool hits 32 | 2026-10-30 | Prevents TCP connection exhaustion DoS |
| Safe Stream Cleanup | `FileInputStream.use` in `TFLiteClassifierImpl.setupInterpreter` | 2026-10-30 | Prevents file descriptor resource leaks |

## Blockers

None.

## Concerns

None.

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
