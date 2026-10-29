---
updated: 2026-10-29T23:51:30+05:30
---

# Project State

## Current Position

**Milestone:** Stabilization and Release v2.1.6
**Phase:** 4 - Memory Baseline & largeHeap Removal
**Status:** executing
**Plan:** Plan 4.1 - 4.2: Remove largeHeap="true" and verify memory baseline

## Last Action

Completed Phase 3: eliminated unconditional `packetBuffer.copyOf(length)` in the reader loop, introduced `ForwardingDnsRequest` with `toForwardingRequest()` for async cache-miss queries, eliminated redundant second allocations in `resolveAndForward`, and verified buffer isolation with unit tests.

## Next Steps

1. Remove `android:largeHeap="true"` from `app/src/main/AndroidManifest.xml`.
2. Verify full build and unit test suite.
3. Commit Phase 4 atomically.

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
