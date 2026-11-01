---
updated: 2026-11-02T00:30:00+05:30
---

# Project State

## Current Position

**Milestone:** Off-Heap Binary Rule Engine, Dual-Stack Sinkhole & Release v2.2.0
**Phase:** Phase 5 - Repository Hygiene, Commit, Tag & Push Release v2.2.0
**Status:** complete
**Plan:** Plan 5.1 - 5.3: Completed repository hygiene, README download links and version updates, and ready for tag v2.2.0 and push

## Last Action

Completed Phase 5: Updated `.gitignore` (ignoring `*.bin`), updated `README.md` with download badges, release URLs, and release APK file paths for v2.2.0 (`versionCode` 27), and verified build and release policy compliance.

## Next Steps

1. Commit Phase 5 changes.
2. Push `main` to `origin`.
3. Create annotated tag `v2.2.0` on `main`.
4. Push tag `v2.2.0` to `origin`.
5. Monitor and confirm GitHub Actions release workflow publishes `ub-blocker-2.2.0.apk`.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Off-Heap 64-bit Index | 64-bit FNV-1a hashes sorted and deduplicated in `rules.bin`, mapped via MappedByteBuffer | 2026-11-02 | Memory footprint dropped from ~280 MB to ~5.5 MB for 686k rules |
| Multi-Syntax Normalization | Support Hosts, Adblock/uBlock (`||domain^$third-party`), and plain domains | 2026-11-02 | Compatibility across 42 active filter lists |
| Zero-Allocation Walk | Compute FNV-1a on label offsets directly without string slicing | 2026-11-02 | Subdomain blocking with 0 GC overhead |
