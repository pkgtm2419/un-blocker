# 04 - Haikei-Style Generated Background Curves & Waves

## Goal
Implement procedural, mathematically generated vector background shapes (Haikei-style organic waves, soft layered contour blobs, and smooth multi-stop mesh gradients) rendered via Jetpack Compose `Canvas` to give the app a polished, bespoke, modern aesthetic.

## Current State
- The application background is currently a flat solid color derived from `MaterialTheme.colorScheme.background` (`#121212` in dark mode, `#F4F6F8` in light mode).
- Screens lack visual atmospheric depth, texture, or brand presence.

## Steps
1. Create `com.unblocker.app.ui.components.background.HaikeiBackground.kt`.
2. Implement procedural spline/bezier curve generation using Compose `Path` and cubic beziers:
   - Upper atmospheric wave contour layered with low-opacity gradient accents.
   - Organic morphing or subtle floating ambient energy blobs aligned with the central shield button.
   - Integration with dynamic state colors (subtle emerald aura when protected, neutral slate when idle, warm amber on warning).
3. Ensure efficient Canvas drawing with minimal recomposition overhead (caching paths with `remember` and sizing with `drawWithCache`).
4. Wrap all primary screens (`UnblockerScreen`, `LogViewerScreen`) in this atmospheric background layer.

## Acceptance Criteria
- Background renders organic, aesthetic Haikei-inspired vector wave surfaces without banding or pixelation.
- Renders adaptively across phone and tablet aspect ratios.
- Zero CPU/battery impact when static; subtle ambient transitions when state shifts.

## Risks & Dependencies
- Path calculations must avoid allocating objects inside `onDraw` loop; use pre-calculated paths scaled by container size.
