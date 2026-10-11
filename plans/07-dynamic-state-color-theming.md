# 07 - Dynamic State-Driven Real-Time Color Theming

## Goal
Implement a dynamic reactive color system that synchronizes the application's color scheme with the live state of the VPN shield, blocking activity, and detected network hazards in real time.

## Current State
- The app uses `UnblockerTheme` supporting Material 3 dynamic theming on API 31+ with fixed fallback palettes (`OneUiDarkColorScheme` / `OneUiLightColorScheme`).
- While the power button transitions color via `animateColorAsState`, the surrounding interface surfaces, cards, borders, navigation bar, and status indicators do not dynamically adjust their harmonic accents to match the active operational mode.

## Steps
1. Create a reactive palette state model: `VpnStateColors`:
   - `ACTIVE`: Emerald/Teal tones, high clarity, confidence green accents.
   - `STARTING` / `STOPPING`: Luminous Amber / Gold pulse accents indicating transitional state.
   - `IDLE`: Muted Slate / Charcoal / Deep Graphite tones representing standby.
   - `WARNING` / `ERROR`: Crimson / Coral accents indicating blocked ports or Private DNS collisions.
2. Expose `LocalVpnStateColors` via Compose `CompositionLocalProvider`.
3. Animate all primary surface accents, glowing borders, card gradients, and status typography smoothly across state transitions with `animateColorAsState`.

## Acceptance Criteria
- All visual components (shield button, status panel, cards, and navigation pill) morph their theme accents seamlessly when VPN connects, disconnects, or errors.
- Respects system dark/light mode while maintaining high WCAG AA/AAA contrast ratios.
- Zero jerky snaps; color transitions use smooth 350ms interpolations.

## Risks & Dependencies
- Must maintain consistent contrast so text remains legible regardless of dynamic state color shift.
