---
updated: 2026-10-30T20:56:00+05:30
---

# Project State

## Current Position

**Milestone:** Performance Optimization, Coalescing & Release Hardening v2.1.7
**Phase:** 11 - Version Bump, Release Build & Packaging (v2.1.7)
**Status:** executing
**Plan:** Plan 11.1 - 11.4: Version bump, assemble release APKs, verify, and release

## Last Action

Completed Phase 10: Implemented DNS fuzz mutation tests covering cyclic compression pointers, corrupt IHL, oversized labels, and random fuzzing; hardened release CI signing fallback.

## Next Steps

1. Bump version to 2.1.7 (`versionCode = 25`) in `app/build.gradle.kts`.
2. Update `README.md` download badges and links to v2.1.7.
3. Run test suite and assemble release APKs.
4. Update checksums and create commit.
5. Push to git and create release.

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
