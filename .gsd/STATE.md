---
updated: 2026-10-29T23:51:30+05:30
---

# Project State

## Current Position

**Milestone:** Stabilization and Release v2.1.6
**Phase:** 7 - Version Bump, Release Build & Packaging
**Status:** executing
**Plan:** Plan 7.1 - 7.4: Version bump to 2.1.6 (code 24), assemble release APK, verify artifacts, push to git

## Last Action

Completed Phase 6: introduced `DomainName.normalizeUserEntry` with `java.net.IDN.toASCII(..., IDN.USE_STD3_ASCII_RULES)`, wired IDNA normalization into custom domain additions, and added comprehensive test cases verifying that Punycode-encoded internationalized domains match wire DNS queries.

## Next Steps

1. Bump `versionCode = 24` and `versionName = "2.1.6"` in `app/build.gradle.kts`.
2. Run release build `:app:assembleRelease`.
3. Verify release APK artifact integrity.
4. Push all commits to `origin main`.
5. Present final summary with artifact verification proof.

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
