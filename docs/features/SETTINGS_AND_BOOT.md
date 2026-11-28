# Filtering settings, quick pause, health and reboot

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt`
- `app/src/main/java/com/unblocker/app/services/QuickStartManager.kt`
- `app/src/main/java/com/unblocker/app/receivers/BootReceiver.kt`
- `app/src/main/java/com/unblocker/app/services/HealthCheckService.kt`

## Current functional contract and verification

Settings include ad/adult toggles, `SMART`/`APPROVED_ONLY`, `BASIC`/`OPTIMAL`/`COMPLETE`, boot intent, health cadence, query-log paused state and 5-minute pause. Startup applies bundled `default_config.json` on first initialization. `BootReceiver` handles `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` while staying `android:exported="false"`, honoring `autoRestartOnBoot`, `protectionEnabled`, and existing prior VPN consent (`VpnService.prepare(context) == null` — never prompting from a receiver). Android may deny/revoke VPN consent, notification, background FGS or boot-start capability; service actual state must be reflected without false optimism.

**Regressions:** first install vs upgrade settings, toggles do not unexpectedly stop service, pause expiry, reboot with consent, app-update replacement (`MY_PACKAGE_REPLACED` — unit tested in `BootReceiverTest`; device `adb install -r` check: `NOT RUN`), force-stop, OEM restriction, health checks use synthetic domains and must not train persistent learner.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
