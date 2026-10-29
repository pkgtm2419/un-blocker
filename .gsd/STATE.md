---
updated: 2026-10-29T23:51:30+05:30
---

# Project State

## Current Position

**Milestone:** Stabilization and Release v2.1.6
**Phase:** 3 - Hot-Path Allocation Reduction
**Status:** executing
**Plan:** Plan 3.1 - 3.3: In-place packet parsing and lightweight forwarding DTO

## Last Action

Completed Phase 2: created `TunResponseWriter` single-writer queue with backpressure handling, completely eliminated global `writeLock` contention across UDP, TCP, blocked, cached, and forwarded paths, added concurrent unit tests, and verified full test suite.

## Next Steps

1. Eliminate `packetBuffer.copyOf(length)` in the reader hot loop by parsing directly in the reusable buffer.
2. Introduce compact immutable `ForwardingDnsRequest` containing only required payload/metadata for async forwarding.
3. Verify buffer isolation and regression tests.
4. Commit Phase 3 atomically.

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
