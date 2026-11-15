# Compose interface and user flows

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/ui/MainScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/screens/UnblockerScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/logs/LogViewerScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/learn/LearnAppScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/extensions/ExtensionsScreen.kt`
- `app/src/main/java/com/unblocker/app/ui/settings/SettingsScreen.kt`

## Current functional contract and verification

`MainScreen` routes protection, logs, learn an app, extensions and settings; Hilt/ViewModels/StateFlows drive screens. Theme uses `SkeuomorphicCard`, `FilteringTierCard`, floating pill nav, theme and reduced-motion primitives. UX copy must distinguish DNS-only filtering from full privacy, and separate active VPN from bypass/limited coverage.

**Regressions:** empty/online/offline states, loading/error, denied VPN permission, TalkBack, font scale, orientation/tablet, dark theme, reduced motion, list paging, no block on Compose main thread, safe CSV sharing.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
