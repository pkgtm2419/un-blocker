---
updated: 2026-10-29T23:51:30+05:30
---

# Project State

## Current Position

**Milestone:** Stabilization and Release v2.1.6
**Phase:** 6 - Rule Correctness & IDNA Canonicalization
**Status:** executing
**Plan:** Plan 6.1 - 6.3: IDNA Punycode conversion for custom rules and internationalized domains

## Last Action

Completed Phase 5: hardened `QueryLogSink` privacy with `HistoryMode.AGGREGATE_ONLY` by default, made raw chronological event logging strictly opt-in, tightened retention cap to 24 hours / 1,000 entries max, integrated Room write exception handling into `VpnDiagnostics`, and verified with unit tests.

## Next Steps

1. Add `DomainName.normalizeUserEntry` with `java.net.IDN.toASCII(..., IDN.USE_STD3_ASCII_RULES)`.
2. Connect IDNA canonicalization to custom user rules and manual rule import boundaries.
3. Add unit tests for IDN / Punycode domains and boundary rules.
4. Commit Phase 6 atomically.

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
