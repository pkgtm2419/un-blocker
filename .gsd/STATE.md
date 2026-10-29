---
updated: 2026-10-29T23:51:30+05:30
---

# Project State

## Current Position

**Milestone:** Stabilization and Release v2.1.6
**Phase:** 5 - Domain and History Privacy Hardening
**Status:** executing
**Plan:** Plan 5.1 - 5.3: Opt-in raw domain history, aggregate counters by default, and safe Room error handling

## Last Action

Completed Phase 4: removed `android:largeHeap="true"` from `app/src/main/AndroidManifest.xml` and verified that debug manifest merges and builds cleanly within normal memory budgets.

## Next Steps

1. Update `QueryLogSink.kt` to default to aggregate counters only; make raw domain history opt-in.
2. Add safe Room database error handling and health state updates.
3. Add unit tests for history modes and privacy invariants.
4. Commit Phase 5 atomically.

## Active Decisions

| Decision | Choice | Made | Affects |
|----------|--------|------|---------|
| JDK Runtime | Adoptium Temurin JDK 21.0.13 LTS in `C:\tools\jdk-21.0.13+9` | 2026-10-29 | Gradle builds, unit tests, Ghidra |
| Saturation Response | Immediate SERVFAIL with transaction ID and original question, keeping reader non-blocking | 2026-10-29 | Phase 1 & 2 |
| Diagnostics Privacy | Strip domain, URL, IP, payload; record only failure code, exception class, queue depth, resolver count | 2026-10-29 | Phase 1 |

## Blockers

None.

## Concerns

- Ensure all writes to the TUN interface remain non-blocking so the reader loop is never delayed by I/O.
