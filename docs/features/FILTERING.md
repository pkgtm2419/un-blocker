# Ad, tracker and optional adult-content filtering

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt`
- `app/src/main/java/com/unblocker/app/logic/ContentFilterEngine.kt`
- `app/src/main/java/com/unblocker/app/logic/AdDetector.kt`
- `app/src/main/java/com/unblocker/app/logic/AdultContentDetector.kt`
- `app/src/main/java/com/unblocker/app/logic/rules/RuleSet.kt`

## Current functional contract and verification

Order is normalize → pause → user allow → user block → `APPROVED_ONLY` early allow → protected/never-block → mobile unbreak → BASIC exceptions → static/extension → adaptive → adult → allow. In current code `APPROVED_ONLY` **does not** block unmatched hosts; it prevents later category evaluation. Adult filter is separately toggled.

**Regressions:** false positives on protected domains, conflicting user rules, exact/suffix/wildcard, BASIC reward/shopping, static allow beating deny, invalid syntax, adult toggle, fail-open exception. See [exact contract](../BEHAVIOR_CONTRACTS.md).

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
