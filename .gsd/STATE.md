---
updated: 2026-10-30T23:15:00+05:30
---

# Project State

## Current Position

**Milestone:** Security Hardening, Direct-DNS Interception & Release v2.1.8
**Phase:** 14 - Version Bump, Verification Suite & Release v2.1.8
**Status:** complete
**Plan:** All plans complete (14.1 - 14.4)

## Last Action

Completed Phase 14: Bumped version to 2.1.8 (`versionCode = 26`), updated README.md badges and direct download links, verified 100% green test suite (Android unit tests, Python tooling tests, shell script tests), assembled release APKs (`ub-blocker-2.1.8.apk` and `ub-blocker-2.1.8-signed.apk`), and updated SHA256 checksums.

## Next Steps

1. Commit Phase 14.
2. Push commits to `origin main`.
3. Create annotated tag `v2.1.8` and push to origin.
4. Publish GitHub Release v2.1.8 with release assets.

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
