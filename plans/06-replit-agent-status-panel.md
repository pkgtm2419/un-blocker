# 06 - Replit Agent-Inspired Diagnostic & Workflow Status Panel

## Goal
Implement a streamlined, transparent Replit Agent-inspired status panel. Give users complete, real-time visibility into the multi-stage background engine (VPN Tunnel interface, DNS Filter Core, Rule Matcher, and Local Evidence Learner) with step status indicators, live telemetry chips, and interactive diagnostic expansion.

## Current State
- The status is indicated by a compact pill (`SHIELD ACTIVE • LOCAL EVIDENCE LEARNING` or `SHIELD IDLE • TAP ICON TO START`).
- While clean, users cannot easily diagnose which specific subsystem is initializing, blocking, or encountering network issues (e.g., if Private DNS is interfering).

## Steps
1. Create `com.unblocker.app.ui.components.agent.AgentStatusPanel.kt`.
2. Design a multi-phase pipeline indicator:
   - Phase 1: `Tunnel Interface` (Loopback bind, interface MTU, route table)
   - Phase 2: `Rule Engine` (Compiled rules loaded, ad/adult detectors active)
   - Phase 3: `DNS Gateway` (Local port 53 listener, upstream health)
   - Phase 4: `Evidence Learner` (HMAC store active, zero remote leaks)
3. Provide an interactive collapsible tray:
   - Compact mode: Sleek status badge with pulsating activity indicator and sub-state summary.
   - Expanded mode: Step-by-step diagnostic verification card with status icons (Success, Running, Warning, Idle).
4. Integrate real-time diagnostics: query count, current resolver mode, and instant quick-action fix (e.g., network settings shortcut if Private DNS is detected).

## Acceptance Criteria
- Clearly communicates exact engine operational stages with Replit Agent-inspired aesthetic (crisp monospaced tags, glowing status pips, clean bordered panels).
- Smooth expand/collapse animation with zero clutter in default view.
- Provides immediate actionable diagnostic information if errors occur.

## Risks & Dependencies
- Must strictly consume local `StateFlow` from `QuickStartManager` and `VpnSessionState` without adding overhead to the packet forwarding loop.
