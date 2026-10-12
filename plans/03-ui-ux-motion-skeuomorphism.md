# Plan 03: UI/UX, Motion Primitives, and Tactile Skeuomorphism

## Objective

Deliver an authentic, cohesive visual system with tactile controls, layered surfaces, motion primitives, and accessibility compliance.

### UI-01: Design tokens & themes
- Cohesive color scheme with support for dark theme and dynamic color.
- High contrast status indicators.
- Semantic state indicators for STOPPED, STARTING, RUNNING, and ERROR.

### UI-02: Skeuomorphic visual system
- Slightly raised primary controls with tactile affordances.
- Pressed states with subtle spring bounces and dual shadows.
- Realistic specular rims and ambient glow rings.

### UI-03: Motion design & accessibility
- Natural spring physics for press interactions.
- Atmospheric breathing pulse for active running state.
- Reduced motion support: if system animator duration scale is 0, freeze animations to avoid battery drain.
- Accessible touch targets (minimum 48dp).

### UI-04: Explicit permission flow
- Before turning on the service, explicitly verify and request any required Android permissions:
  - Notification permission (`POST_NOTIFICATIONS` on Android 13+)
  - VPN permission (`VpnService.prepare()`)
- If denied, do not turn on service and keep UI state truthful.
