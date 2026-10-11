# 05 - Skeuomorphic & Tactile Depth Surface Architecture

## Goal
Elevate the UI layout design with skeuomorphic tactile affordances: authentic directional lighting, dual-layer specular highlights, recessed/embossed card surfaces, tactile toggle bevels, and physical push-button depths reminiscent of premium physical audio and network hardware (Godly-level craft).

## Current State
- Cards and surfaces use standard flat Material 3 elevation (`CardDefaults.cardElevation(defaultElevation = 1.dp)`) with simple 1.dp outline borders.
- The power button has radial gradient glow and elevation, but secondary cards, switches, and panels lack physical tactile depth.

## Steps
1. Create `com.unblocker.app.ui.components.skeuomorphic.TactileCard.kt`:
   - Double-border lighting model: top-left specular edge (light source reflection) + bottom-right shadow bevel.
   - Dual-shadow elevation: ambient soft blur + directional key drop shadow.
   - Realistic recessed/inset styling for search boxes, input fields, and inactive states.
2. Build `TactileSwitch`:
   - A physical thumb switch with metallic or glossy finish, sunken track groove, and tactile click response.
3. Apply tactile card treatments to:
   - Primary Shield Action container.
   - Coverage & Limits information card.
   - Local Domain Exception rule builder.
   - LogViewer summary cards and expanded domain rows.
4. Harmonize skeuomorphic physical realism with clean, modern typography and spacing.

## Acceptance Criteria
- Cards display noticeable physical depth, directional light reflections, and tactile bevels.
- Button and card interactions visually mimic physical hardware controls (depression on press, release rebound).
- Retains full dark mode and light mode readability and contrast ratios.

## Risks & Dependencies
- Complex shadow stacks and multiple blur passes can impact rendering performance on lower-end Android devices; use hardware-accelerated drawing and efficient brush gradients.
