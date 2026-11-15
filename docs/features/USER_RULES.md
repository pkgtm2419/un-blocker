# Private user exceptions and overrides

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/logic/analysis/DeviceLearning.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/PrivateDomainSet.kt`
- `app/src/main/java/com/unblocker/app/data/repository/UserRuleGateway.kt`
- `app/src/main/java/com/unblocker/app/data/repository/LegacyUserRuleMigration.kt`

## Current functional contract and verification

Device-keyed HMAC domain identifiers back user-specific allow/block membership (nominal caps 1000 each) and whole-site/ancestor matching. User allow runs before user block; user block runs before never-block in the current decision usecase. The app cannot list original plaintext rules from HMAC; removal requires entering a domain or clearing entries.

The separate **My domains** protection list is readable: `MyDomainsStore` uses private SharedPreferences and caps it at 500 names. `DeviceUserRuleGateway.protectDomain()` writes there; `ContentFilterEngine` supplies it to `DefaultProtectedDomains`. Do not extend the HMAC storage claim to that feature.

**Regressions:** normalize and reject malformed entries, allow-vs-block conflict, ancestor scope, migrated v1 keyed rules, key unavailable, cap enforcement and no plaintext backup. Evaluate protected infrastructure before changing precedence.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
