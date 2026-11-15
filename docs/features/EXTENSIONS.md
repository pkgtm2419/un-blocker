# Extension filter source catalog and updates

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt`
- `app/src/main/java/com/unblocker/app/data/repository/BoundedFilterDownloader.kt`
- `app/src/main/java/com/unblocker/app/logic/rules/RuleLineParser.kt`
- `app/src/main/assets/extensions/extensions_manifest.json`

## Current functional contract and verification

Current manifest v6 includes **19** records; feature supports enable/disable, custom list URLs, manual update and optional auto-update preference (default off in source). Remote sources are untrusted data: HTTPS required, redirects/caps and syntax/rule admission applied, atomic temp replacement/rollback expected.

**Regressions:** HTTPS-only, missing/redirected/private target, DNS rebinding, 15 MiB/300k-rule/4096-byte caps, cosmetic rejection, invalid manifests, offline download, rollback, disabled filters and user communication of errors.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
