---
updated: 2026-11-02T23:45:00+05:30
---

# Project State

## Current Position

**Milestone:** Superpowers Improvement Plans & Release v2.3.0
**Phase:** Phase 6 - Superpowers Improvement Plans & Release v2.3.0
**Status:** ready
**Plan:** Plan 6.1 - 6.3: Bump version to 2.3.0 (versionCode 28), update README, tag v2.3.0, and push to trigger automated release

## Last Action

Completed all 6 architectural improvement plans from `START_HERE.txt`, passed 812 unit tests and 19 policy scripts, bumped app version to `2.3.0` (`versionCode 28`), and updated README download links.

## Next Steps

1. Commit version bump and README updates.
2. Push `main` to `origin`.
3. Create annotated tag `v2.3.0`.
4. Push tag `v2.3.0` to `origin`.
5. Monitor GitHub Actions `Publish release APK` workflow to completion.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Off-Heap 64-bit Index | 64-bit FNV-1a hashes sorted and deduplicated in `rules.bin`, mapped via MappedByteBuffer | 2026-11-02 | Memory footprint dropped from ~280 MB to ~5.5 MB for 686k rules |
| Multi-Syntax Normalization | Support Hosts, Adblock/uBlock (`||domain^$third-party`), and plain domains | 2026-11-02 | Compatibility across 42 active filter lists |
| Zero-Allocation Walk | Compute FNV-1a on label offsets directly without string slicing | 2026-11-02 | Subdomain blocking with 0 GC overhead |
