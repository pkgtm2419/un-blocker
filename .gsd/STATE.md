---
updated: 2026-10-30T20:56:00+05:30
---

# Project State

## Current Position

**Milestone:** Performance Optimization, Coalescing & Release Hardening v2.1.7
**Phase:** 11 - Version Bump, Release Build & Packaging (v2.1.7)
**Status:** complete
**Plan:** All plans complete (11.1 - 11.4)

## Last Action

Completed Phase 11: Version bumped to 2.1.7 (versionCode 25), updated README.md badges and links, verified all 116 test tasks pass with zero regressions, assembled release APKs (`ub-blocker-2.1.7.apk` and `ub-blocker-2.1.7-signed.apk`), and calculated SHA256 sums.

## Next Steps

1. Commit Phase 11.
2. Push commits and tags to origin.
3. Publish GitHub Release v2.1.7 with release assets.
4. Provide comprehensive delivery summary.

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
