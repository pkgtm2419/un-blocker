---
updated: 2026-11-02T00:30:00+05:30
---

# Project State

## Current Position

**Milestone:** Off-Heap Binary Rule Engine, Dual-Stack Sinkhole & Release v2.2.0
**Phase:** Phase 5 - Repository Hygiene, Commit, Tag & Push Release v2.2.0
**Status:** ready
**Plan:** Plan 5.1 - 5.2: Working tree verification, git tag v2.2.0, push main and tag to GitHub

## Last Action

Completed Phase 4: Appended Proguard keep rules for Room, JNI, TFLite, and Coroutines in `app/proguard-rules.pro`; configured `app/build.gradle.kts` with `versionCode = 27`, `versionName = "2.2.0"`, and lint options; updated `.github/workflows/codeql.yml` and `.github/workflows/release.yml`. Verified with `./gradlew testDebugUnitTest lintDebug assembleRelease` (All 419 tasks succeeded, release APK produced).

## Next Steps

1. Verify git working tree cleanliness and `.gitignore`.
2. Commit Phase 4 changes: `git commit -m "chore(phase-4): r8 keep rules, lint stabilization, and github actions repair"`.
3. Create git tag `v2.2.0`.
4. Push `main` and tag `v2.2.0` to `origin`.
5. Verify GitHub Actions release workflow execution.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| Off-Heap 64-bit Index | 64-bit FNV-1a hashes sorted and deduplicated in `rules.bin`, mapped via MappedByteBuffer | 2026-11-02 | Memory footprint dropped from ~280 MB to ~5.5 MB for 686k rules |
| Multi-Syntax Normalization | Support Hosts, Adblock/uBlock (`||domain^$third-party`), and plain domains | 2026-11-02 | Compatibility across 42 active filter lists |
| Zero-Allocation Walk | Compute FNV-1a on label offsets directly without string slicing | 2026-11-02 | Subdomain blocking with 0 GC overhead |
