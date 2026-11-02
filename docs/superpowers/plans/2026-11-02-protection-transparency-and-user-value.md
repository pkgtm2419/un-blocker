# Protection Transparency and User Value Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver trustworthy, practical user benefits using existing local DNS events without network analytics, broad packet interception or meaningful extra battery/CPU load.

**Architecture:** Make the current VPN state and domain-decision reason visible through lifecycle-aware Compose state. Route all corrections through the single authoritative private user-rule gateway from Phase 2. Build lightweight optional diagnostics and summary views on bounded local counters; any temporary exception expires lazily without continuous timers.

**Tech Stack:** Kotlin, Jetpack Compose, lifecycle-aware StateFlow, Room, Android VpnService, JUnit and Compose UI testing.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- User experience must be truthful: this is a DNS-only local filter, **not** an anonymity VPN, TLS inspection tool, antivirus or guaranteed parental-control enforcement.
- No accounts, ads, telemetry or background upload. Local statistics and rule explanation are optional and privacy-scoped.
- Do not introduce periodic polling for UI widgets; use flows and callbacks from events that already exist.
- Preserve screen reader labels, large text, reduced motion and offline functionality.
- Prior phases must make Logs allow/block controls actually affect `ContentFilterEngine` before marketing new user controls.

## Review Focus

1. With Private DNS strict enabled, dashboard must show limited coverage and a concrete mitigation, not `Protected all traffic` (Task 1).
2. After a manual `Allow`, the affected domain must resolve under the VPN without an app restart (Task 2).
3. Temporary exception must expire without running a timer or WorkManager wakeup every minute (Task 2).
4. Turning off logging must not upload, display or retain new raw query history under the user's interpretation of `Paused` (Task 3).
5. Large text, TalkBack, small device and reduced-motion mode must not hide protection state or the primary action (Task 4).

---

## User-value priority matrix

| Feature | Value | Incremental resource cost | Priority |
|---|---|---|---|
| Correct one-tap Unblock / Block | Very high: resolves a broken site immediately | Negligible beyond an existing local rule write | P0/P1 dependency |
| Why blocked / reason category | High: actionable, transparent | Low: existing decision reason codes and local stats | P1 |
| Honest coverage status and Private DNS help | High: prevents false security confidence | Low: reuses observed VPN/Private DNS flags | P1 |
| Privacy-first local daily blocked DNS count | Medium-high: confirms function without data collection | Low if bounded incremental counters | P2 |
| Reduced motion / Quiet resources | Medium: battery/user control | Near-zero with lifecycle-aware UI | P2 |
| Per-app filtering/routing | Potentially high, but not safe with current DNS-only tunnel | High networking/correctness complexity | Defer |
| Browser DoH bypass blocking or full traffic forwarding | High complexity; privacy/energy tradeoffs | High | Out of scope |

### Task 1: Accurate protection status with actionable coverage explanation

**Files:**
- Create: `app/src/main/java/com/unblocker/app/ui/model/ProtectionUiState.kt`.
- Create: `app/src/main/java/com/unblocker/app/ui/model/ProtectionUiStateMapper.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/screens/UnblockerScreen.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/components/AgentStatusPanel.kt`.
- Test: create `app/src/test/java/com/unblocker/app/ui/model/ProtectionUiStateMapperTest.kt`.

**Interfaces:** `data class ProtectionUiState(val label: String, val detail: String, val action: RecoveryAction?, val status: ServiceStatus)`; `fun ProtectionUiStateMapper.from(status: ServiceStatus, privateDnsActive: Boolean, resolverAvailable: Boolean): ProtectionUiState`. Use `Protected (standard DNS)`, `Limited coverage (Private DNS)`, `Starting`, `Off`, `Error`, never an unconditional `100% protected` badge.

- [ ] Write failing state-map tests: VPN OFF, RUNNING with regular DNS, strict Private DNS active, no usable resolver, permission denied, service restarting.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.unblocker.app.ui.model.ProtectionUiStateMapperTest'` and confirm initial failures.
- [ ] Implement mapper and render compact, screen-reader-friendly status with one recovery action (`Open Android Private DNS settings`, `Retry`, etc.).
- [ ] Add Compose instrumentation screenshot/assertion tests for each state. Verify strings do not claim interception of browser DoH or non-DNS traffic.
- [ ] Commit `feat(ui): explain actual DNS coverage and recovery options`.

### Task 2: A reliable `Fix this site` action

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/ui/logs/LogViewerScreen.kt` and `LogViewModel.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/screens/UnblockerScreen.kt` to link to recent locally blocked domains if logging is enabled.
- Create: `app/src/main/java/com/unblocker/app/data/repository/TemporaryAllowStore.kt` (only if user research confirms temporary exceptions are useful).
- Test: create `app/src/test/java/com/unblocker/app/ui/logs/RuleRecoveryFlowTest.kt`.
- Test: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt` live DNS case.

**Interfaces:** Reuse `UserRuleGateway.set(domain, UserFeedback.ALLOW/BLOCK/NONE)` from the rule-consistency plan. If implementing temporary allow, expose `suspend fun allowUntil(domain: String, deadlineElapsedRealtimeMs: Long)` and `fun isTemporarilyAllowed(domain: String, nowElapsedRealtimeMs: Long): Boolean`. Do not persist precise visited domains merely to schedule expiry; expiry checked lazily when matching.

- [ ] Add tests for tap Allow on blocked domain, verification of active VPN decision, explicit Undo, app restart and invalid input.
- [ ] Add error state: disk/Keystore write failure leaves existing block policy and tells user that the change was not applied.
- [ ] Implement persistent allow/block first; only then consider temporary allow with expiry, avoiding a new clock timer/service. Ensure expiration on process restart is documented (ephemeral) unless specifically designed otherwise.
- [ ] Run Compose UI tests and Android TUN DNS test for the exact domain transition; commit `feat(ui): make blocked-site recovery reliable`.

### Task 3: Privacy-first local protection summary

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/ui/logs/LogViewModel.kt` and `LogViewerScreen.kt`.
- Modify: `data-store/src/main/java/com/unblocker/data/db/dao/DomainLogDao.kt` if an efficient grouped summary query is needed.
- Modify: `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt` if new local-only summary preference is needed.
- Test: `app/src/androidTest/java/com/unblocker/app/LogRetentionAndClearTest.kt`.

**Interfaces:** `data class LocalProtectionSummary(val dnsQueries: Long, val blockedDnsQueries: Long, val firstObservedMs: Long?, val lastObservedMs: Long?)`. Use a pre-existing bounded local database/counter store. Never label DNS query count as number of ads actually removed, people protected, or bytes saved. Do not add background traffic or collection on opt-out.

- [ ] Test summary for zero data, 24-hour retention boundary, all allowed, all blocked, and after clear/pause.
- [ ] Add one simple summary card with counts, filter state and explanation of what a DNS request means; show `No data` if logging disabled.
- [ ] Reuse DAO flows and memoized calculations, rather than polling every second or rendering one row per query.
- [ ] Run tests and measure extra UI frame cost and DB reads; commit `feat(ui): show honest private protection summary`.

### Task 4: Low-resource mode and accessibility

**Files:**
- Create: `app/src/main/java/com/unblocker/app/ui/motion/ReducedMotionPolicy.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/motion/MotionPrimitives.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/components/AgentStatusPanel.kt` and `ui/screens/UnblockerScreen.kt`.
- Modify: `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt`.
- Test: create `app/src/androidTest/java/com/unblocker/app/ReducedMotionAccessibilityTest.kt`.

**Interfaces:** `data class ReducedMotionPolicy(val disableDecorativeLoops: Boolean, val durationScale: Float)` derived from system accessibility/animation settings plus opt-in `Quiet UI` setting. This policy affects only Compose decoration and optional summaries, **never VPN DNS processing**.

- [ ] Test no infinite glow/pulse when reduced motion is on or screen not composed, while primary VPN state still updates.
- [ ] Test TalkBack focus order, descriptions, tap targets, responsive/large fonts, no clipped recovery actions on compact screens.
- [ ] Implement Quiet UI switch (persisted); reduce continuously animated gradients, pulse and redundant recomposition with lifecycle-aware collection.
- [ ] Profile UI frame timing on low-memory Android device before/after, ensure no functional regression; commit `feat(ui): accessible low-resource presentation`.

## Exit gates

- State and notifications accurately reflect standard-DNS interception and known bypasses.
- One-tap unblock/Undo changes actual VPN filter decisions with tests.
- No new telemetry, cloud accounts or persistent profiling. Optional local summaries are bounded, clearable and user controlled.
- No always-on polling or background timer added for UI/temporary rules. Reduced motion works while VPN still filters.
- Compose and real-device acceptance tests pass in both compact and large text configurations.
