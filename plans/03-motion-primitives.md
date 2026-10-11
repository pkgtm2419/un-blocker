# 03 - Reusable Motion Primitives Component Architecture

## Goal
Establish a high-performance, reusable motion and animation primitives design system for Jetpack Compose. Replace scattered inline animations with composable primitives for bounce presses, rhythmic pulses, slide-fades, and organic state transitions.

## Current State
- Animations in `UnblockerScreen.kt` and `LogViewerScreen.kt` are largely implemented inline using standard Compose APIs (`animateColorAsState`, `animateFloatAsState`, `rememberInfiniteTransition`).
- There is no central motion primitives package or reusable interaction modifier library.
- Press states, tactile bounciness, and glow expansions are tied directly to single screen elements.

## Steps
1. Create a dedicated package `com.unblocker.app.ui.motion` containing core motion primitives:
   - `BouncyInteractionModifier`: Spring-based physics modifier responding to user touch press/release with authentic mechanical spring damping.
   - `BreathingGlow`: Infinite rhythmic pulse primitive with custom easing, amplitude, and period.
   - `AnimatedContentSwitcher`: Expressive animated container for fluid transitions between status states.
   - `TactileHaptic`: Standardized haptic feedback triggers coupled with motion moments.
2. Refactor `UnblockerScreen.kt` to compose these reusable primitives instead of hardcoded inline transitions.
3. Apply motion primitives to the navigation pill bar, power switch, list items, and status chips.

## Acceptance Criteria
- Reusable motion components reside in `ui/motion` with modular APIs.
- Touch interactions exhibit smooth 60/120fps physics-based spring animations without frame drops.
- Motion curves adhere to Google Material 3 and modern tactile interaction standards.

## Risks & Dependencies
- Excessively aggressive infinite animations can cause CPU wakeups and battery drain; primitives must pause or throttle when the screen is in background or lifecycle changes.
