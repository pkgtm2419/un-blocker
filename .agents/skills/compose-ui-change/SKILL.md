---
name: compose-ui-change
description: Use when designing or modifying Jetpack Compose screens, navigation, themes, settings interactions or accessibility.
---

# Compose Ui Change

## Steps
1. Inspect `DESIGN.md`, `docs/UI_UX.md`, `MainScreen`, current theme/components and screen ViewModels. Identify states: normal/empty/loading/error/permission denial/limited DNS coverage.
2. Reuse Material3 and existing `SkeuomorphicCard`, `FloatingPillNavBar`, adaptive layout and reduced-motion patterns; avoid adding dependencies for trivial UI work.
3. Hoist state; collect flows lifecycle-safely and avoid disk/network access inside composables or aggressive recomposition. Keep clear error/recovery copy.
4. Test TalkBack labels, dynamic type, contrast, orientation, keyboard targets, dark/light themes, motion preference and performance on log lists.
5. Run ViewModel/mapper unit tests and emulator UI verification where available; add UI regression tests for actual state changes.
6. Update `DESIGN.md`, `docs/UI_UX.md`, feature guide, matrix and changelog if user-visible.

## Example
When changing protection status card, distinguish VPN `RUNNING` with strict Private DNS bypass from fully intercepting DNS.

## Mistake
Rendering "Protected" solely because a Start button was tapped.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
