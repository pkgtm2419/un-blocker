# DNS-only local VPN protection

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`
- `app/src/main/java/com/unblocker/app/services/QuickStartManager.kt`
- `app/src/main/java/com/unblocker/app/ui/screens/UnblockerScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/model/ProtectionUiStateMapper.kt`

## Current functional contract and verification

Start requires Android `VpnService.prepare()` consent; a foreground service establishes DNS-only capture and a status notification. Start/stop and VPN session status are separate from whether a DNS resolver is available or strict Private DNS bypasses interception. Android/OEM restrictions can prevent boot restart and force-stop recovery. Show `Off`, `Starting`, `Error`, `Limited coverage` and actual `Running` consistently.

**Regressions:** permission denied/revoked, protected DNS, resolver disappears, process death, rapid start/stop, notification pause, boot consent, non-DNS traffic unaffected. Device verification is required; do not assume a green UI proves effective filtering.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
