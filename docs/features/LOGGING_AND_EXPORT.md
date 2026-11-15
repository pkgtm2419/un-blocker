# Local DNS statistics and export

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt`
- `data-store/src/main/java/com/unblocker/data/db/dao/DomainLogDao.kt`
- `app/src/main/java/com/unblocker/app/ui/logs/LogViewModel.kt`
- `app/src/main/java/com/unblocker/app/data/export/DomainExporter.kt`

## Current functional contract and verification

`QueryLogSink` defaults to aggregate-only raw-event mode, but the separate logging pause flag defaults off, so local Room `domain_stats` can record **readable domain names**. Raw event history is opt-in; verify implementation retention before public claims. Logs screen supports local search/sort/filter, pause/clear, user rule actions and user-directed CSV via SAF.

The sink has an opt-in API for raw events, but no production caller enabling it was found. Approximately hourly maintenance deletes events older than 24 hours and beyond 1,000 global rows, and aggregates older than 30 days and beyond 10,000 rows. These bounds are not enforced on every insertion. See [privacy data flow](../PRIVACY_DATA_FLOW.md) for disclosure differences.

**Regressions:** clear barrier under concurrent producer, no resurrection of pre-clear entries, retention caps, database errors, paused sink, opt-in raw history, no Logcat disclosure, CSV injection escaping, export cancel/permission errors.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
