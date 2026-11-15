# Android interface and design contract

## Current design language

Un-Blocker uses Jetpack Compose with Material 3 themes and component-level skeuomorphic treatments, adaptive layouts and a floating-pill navigation bar. This file describes source-observed patterns, not a fixed unverified visual specification.

## UI source of truth

- `app/src/main/java/com/unblocker/app/ui/MainScreen.kt`: navigation and content routing.
- `ui/screens/UnblockerScreen.kt`: protection controls, tiers and status.
- `ui/logs/LogViewerScreen.kt`: log and domain-rule interactions.
- `ui/learn/LearnAppScreen.kt`, `ui/extensions/ExtensionsScreen.kt`, `ui/settings/SettingsScreen.kt`: feature screens.
- `ui/components/SkeuomorphicCard.kt`, `FloatingPillNavBar.kt`, `FilteringTierCard.kt`, `AgentStatusPanel.kt`: reusable elements.
- `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `VpnStateColors.kt`: canonical color/typography/status styles.
- `ui/adaptive/AdaptiveLayout.kt` and `ui/motion/ReducedMotionPolicy.kt`: adaptation and reduced-motion guidance.

## Rules for changes

1. Reuse existing theme tokens and component patterns; do not hardcode unrelated colors or break dark/light themes.
2. VPN status must differentiate stopped, starting, running, limited coverage, error and recovery actions. Labels must reflect actual service state, not optimistic intent.
3. Android VPN/notification consent flows must be understandable and recoverable; never fake protected status.
4. Ensure touch target size, TalkBack semantics, contrast, focus order, long text, orientation, insets, font scaling, reduced motion and keyboard navigation as applicable.
5. Avoid blocking work in composables; favor ViewModels and stable state flows. Avoid uncontrolled recomposition and log-list churn.
6. Test empty/loading/error/data/denied-permission states, and protect user settings across navigation.
7. Update [docs/UI_UX.md](docs/UI_UX.md), screenshots/acceptance notes (when available) and corresponding UI feature docs in the same PR.
