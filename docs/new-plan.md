# Un-Blocker: Main Branch Audit & Engineering Improvement Plan

Repository: [pkgtm2419/un-blocker](<https://github.com/pkgtm2419/un-blocker>) Branch: `main` Audit date: October 12, 2026 Primary objective: **Improve security, privacy, reliability, performance, and UI/UX without breaking existing features.**

I've started reviewing the repository's main-branch documentation and project structure. The project already has a substantial foundation: an Android DNS blocker using Kotlin, Jetpack Compose, Coroutines, `VpnService`, Android Keystore, local filtering rules, and on-device learning. It also documents privacy boundaries, DNS-over-TCP fallback, IPv6 handling, and automated tests. These are important foundations to preserve rather than replace indiscriminately. 【turn1view0】

Your requirements call for more than a visual redesign. They require a coordinated review of the network engine, Android lifecycle, data storage, security boundaries, resource consumption, and interface architecture.

I'll use these priorities throughout the plan:

- **Privacy first:** Keep filtering, learning, configuration, and sensitive-data processing on the device.
- **Security first:** Validate network inputs, protect the VPN lifecycle, minimize exposed interfaces, and prevent sensitive-data leakage.
- **Zero-regression migration:** Preserve existing functionality and verify behavior before and after each change.
- **Resource efficiency:** Minimize battery consumption, CPU time, memory allocation, disk writes, and unnecessary network traffic.
- **High-quality UI:** Combine motion primitives, Haikei-inspired visual assets, real-time color adaptation, Godly-inspired design references, Replit Agent-style implementation workflows, and skeuomorphic interface details.
- **Evidence-based completion:** Distinguish documented features from tested behavior, measured performance, and work that still needs verification.

One important distinction: a local DNS blocker can improve privacy by preventing connections to known tracking domains, but it cannot make the entire device anonymous or stop every form of tracking. DNS queries that need an external resolver still leave the device, and some applications can bypass DNS filtering. The project itself documents these limitations. 【turn1view0】

## 1\. Main-branch audit findings

I also reviewed the existing improvement tracker, coverage baseline, DNS bypass matrix, and local privacy implementation notes.

Existing documentation already records several significant improvements, including DNS packet validation, a binary rule engine, CNAME poisoning protection, private local learning, VPN lifecycle fixes, and automated regression tests. However, it also explicitly identifies production acceptance work that has not been completed. ([Improvement tracker](<https://github.com/pkgtm2419/un-blocker/blob/main/docs/improvement-progress.md>), [local privacy update](<https://github.com/pkgtm2419/un-blocker/blob/main/docs/local-privacy-update.md>))

The following findings should guide the next development phase.

### 1.1 High-priority issues and gaps

| ID | Area | Finding | Priority | Required solution |
| --- | --- | --- | --- | --- |
| AUD-01 | Performance | Battery, CPU, and memory targets have not been independently verified on physical devices. | P0 | Establish reproducible baselines and test under realistic workloads. |
| AUD-02 | Security | Passing automated tests does not establish that the entire application is free from vulnerabilities. | P0 | Conduct a threat-model-driven security review of the VPN, DNS parser, persistence, and release pipeline. |
| AUD-03 | Privacy | DNS-over-HTTPS, Android Private DNS, and direct-IP connections can bypass DNS filtering. | P0 | Improve bypass detection, communicate coverage accurately, and test supported configurations. |
| AUD-04 | Reliability | Physical-device reboot behavior and manufacturer-specific background restrictions remain incompletely verified. | P0 | Test startup, shutdown, network transitions, and recovery across physical devices. |
| AUD-05 | DNS accuracy | The documented evaluation corpus is small, and tracker coverage is weaker than advertising-domain coverage. | P1 | Expand the labeled dataset and test false positives and false negatives before modifying rules. |
| AUD-06 | Data protection | HMAC-based identifiers protect stored domain representations, but they are pseudonymous rather than anonymous. | P1 | Review key lifecycle, reset semantics, file integrity, corruption recovery, and retention. |
| AUD-07 | UI architecture | The requested design system has not yet been established as a complete, regression-tested visual system. | P1 | Introduce reusable design tokens and Compose components before migrating screens. |
| AUD-08 | Accessibility | The redesign needs explicit verification of screen readers, contrast, touch targets, and reduced motion. | P1 | Add accessibility requirements to design and release acceptance tests. |
| AUD-09 | Network efficiency | There is no independently measured battery or network-performance guarantee. | P1 | Measure packet handling, resolver latency, retransmissions, cache effectiveness, and idle overhead. |
| AUD-10 | Release engineering | Production signing readiness and long-duration physical-device testing remain acceptance requirements. | P1 | Verify signing continuity, artifact provenance, and release reproducibility. |
| AUD-11 | Documentation | The README and historical verification notes contain version references that need reconciliation. | P1 | Establish one authoritative source for version, build, test, and release status. |
| AUD-12 | Regression prevention | The new interface and security changes could affect existing settings, rules, and VPN behavior. | P0 | Establish a feature inventory and regression gates before implementation. |

**Important:** These are audit findings and verification gaps, not twelve confirmed exploitable vulnerabilities. Confirming a vulnerability requires inspecting the relevant implementation and reproducing the behavior.

### 1.2 Specific findings from the existing documentation

**A. Performance claims need physical-device evidence**

The project documents a streaming-oriented DNS architecture, a bounded rule engine, and asynchronous persistence. These are promising implementation choices.

However, the README explicitly states that a 24-hour physical-device soak and independently measured battery and CPU targets have not yet been established. (README)

The solution is not to optimize everything preemptively. It is to measure first, identify expensive paths, and optimize the paths shown to be costly.

**B. Filtering accuracy needs a broader evaluation corpus**

The documented coverage baseline reports:

- Advertising hosts: 18 of 19 blocked.
- Tracker hosts: 13 of 22 blocked.
- Combined advertising and tracker recall: 31 of 41.
- Necessary hosts incorrectly blocked: 0 of 58 in the evaluated corpus.

These figures are useful for regression testing, but the corpus is too small to establish general real-world effectiveness. The report also explicitly distinguishes certain vendor service endpoints from their main websites. ([Coverage baseline](<https://github.com/pkgtm2419/un-blocker/blob/main/docs/coverage-baseline.md>))

The plan should therefore expand the test dataset without blindly adding more blocking rules. An aggressive blocklist can reduce privacy in practice if it breaks connectivity, causes retries, or forces users to disable protection.

**C. Privacy protections need end-to-end verification**

The documented design already avoids cloud synchronization, remote learning, analytics, and plaintext persistence of learned domains. It also uses Android Keystore-backed HMAC identifiers and excludes private data from backup. ([Privacy implementation notes](<https://github.com/pkgtm2419/un-blocker/blob/main/docs/local-privacy-update.md>))

The next step is to verify these guarantees across the entire application, including dependencies, crash reporting, debug logging, exported components, notifications, and release builds.

**D. DNS filtering must remain distinct from full-device network protection**

The application is a DNS blocker, not a general-purpose anonymity VPN. Replacing its split-tunnel behavior with a full-device tunnel, or adding TLS interception, would substantially change its security and performance characteristics.

The improvement plan must preserve the existing DNS-only architecture unless a separately justified architectural change is proposed and validated.

**E. Version and verification records need reconciliation**

The current README describes release APK `2.0.9`, while another source-version reference identifies `2.0.6`. Historical implementation notes also reference earlier versions and branches. (README, [local privacy update](<https://github.com/pkgtm2419/un-blocker/blob/main/docs/local-privacy-update.md>))

These differences may reflect legitimate release history rather than a broken build. Nevertheless, the repository needs a single, verified mapping between the source commit, application version, test results, APK, and signing certificate.

### 1.3 What remains unverified

I could inspect the public repository and its documentation, but I could not execute the Android project, run tests against your device, or perform a complete source-level security assessment in this session.

Consequently, I will not claim that I have identified every defect or that the proposed changes have already been implemented.

The plan below makes those remaining inspections explicit and assigns acceptance criteria to them.

---

## 2\. Proposed plan files

I recommend creating the following files under the existing `plans/` directory.

| File | Purpose |
| --- | --- |
| `plans/README.md` | Master roadmap, dependencies, priorities, and release gates. |
| `plans/01-security-and-privacy-hardening.md` | Threat model, DNS security, storage protection, privacy boundaries, and secure releases. |
| `plans/02-performance-battery-and-network.md` | CPU, memory, battery, DNS latency, network efficiency, and soak testing. |
| `plans/03-ui-ux-motion-skeuomorphism.md` | Compose architecture, design tokens, visual references, animations, accessibility, and migration. |
| `plans/04-regression-and-quality-assurance.md` | Existing-feature inventory, automated tests, physical-device matrix, and acceptance criteria. |
| `plans/05-dns-accuracy-and-bypass-coverage.md` | Rule quality, encrypted DNS bypasses, false positives, coverage measurement, and resolver behavior. |
| `plans/06-release-readiness-and-documentation.md` | Version consistency, dependency integrity, signing, release verification, and documentation. |

The files should complement the existing `docs/` records rather than replace them.

**Recommended implementation order:** establish the regression baseline, complete security and privacy review, measure performance, implement the design system, migrate the interface incrementally, and then complete release acceptance testing.

## 3\. `plans/README.md` — Master engineering roadmap

```
# Un-Blocker Engineering Improvement Roadmap

Status: Proposed
Target branch: main
Project: Un-Blocker for Android
Architecture: Kotlin, Jetpack Compose, Coroutines, Android VpnService
Principles: Privacy-first, on-device processing, security, reliability,
resource efficiency, accessibility, zero-regression migration.

## 1. Objectives

1. Strengthen application and network security.
2. Preserve local-only filtering, learning, settings, and diagnostics.
3. Reduce battery, CPU, memory, storage, and network overhead.
4. Improve DNS correctness and filtering accuracy.
5. Introduce a cohesive, motion-aware skeuomorphic design system.
6. Preserve every existing feature during implementation.
7. Establish reproducible testing and release verification.
8. Document all known limitations and unresolved risks.

## 2. Non-Negotiable Constraints

- Do not upload DNS queries, domains, learning data, or browsing history.
- Do not introduce analytics, advertising SDKs, cloud synchronization,
  remote inference, or mandatory accounts.
- Do not silently replace Android-configured DNS resolvers.
- Do not introduce TLS interception or certificate installation.
- Do not change the DNS-only routing architecture without a separate,
  reviewed architecture proposal.
- Do not remove existing settings, rules, filtering, or lifecycle behavior.
- Do not persist raw domain names unnecessarily.
- Do not perform expensive work on the DNS packet-processing path.
- Do not run continuous animations or polling when they are unnecessary.
- Do not release changes while required regression tests are failing.

## 3. Phase Plan

Phase 0: Repository inventory and baseline
Phase 1: Security and privacy hardening
Phase 2: DNS correctness and bypass coverage
Phase 3: Performance and battery optimization
Phase 4: Design system and accessibility
Phase 5: Incremental UI migration
Phase 6: Physical-device verification
Phase 7: Release readiness

Each phase requires documented results before it is considered complete.

## 4. Workstream Status

All work in this roadmap is proposed until implemented and verified.

- [ ] SEC: Security and privacy review
- [ ] DNS: Filtering correctness and bypass analysis
- [ ] PERF: CPU, memory, battery, and network measurements
- [ ] UI: Design tokens and reusable components
- [ ] A11Y: Accessibility verification
- [ ] REG: Existing-feature regression suite
- [ ] REL: Release and signing verification

## 5. Definition of Done

A change is complete only when:

1. Its requirements and affected components are documented.
2. Existing behavior has been recorded before modification.
3. Focused tests have been added or updated.
4. The relevant unit and instrumentation tests pass.
5. Security and privacy implications have been reviewed.
6. Performance has been measured when the change affects a hot path.
7. Accessibility has been checked for interface changes.
8. The source version and documentation are consistent.
9. No unrelated feature or configuration has been silently removed.
10. Remaining limitations are explicitly documented.

## 6. Rollback Policy

- Make changes in small, reviewable commits.
- Preserve a known-good build and baseline test results.
- Keep the previous UI implementation available during migration.
- Do not combine DNS-engine rewrites with cosmetic UI changes.
- Revert any change that causes an unexplained regression.
- Never roll back security fixes without documenting the risk.

## 7. Final Release Gate

A production release requires:

- Passing automated tests and static analysis.
- Successful debug and release builds.
- Verified release signing and artifact provenance.
- DNS correctness and false-positive regression checks.
- Network transition and VPN lifecycle tests.
- Physical-device battery and memory measurements.
- Accessibility verification.
- Privacy-boundary verification.
- No unresolved release-blocking security findings.
```

---

# 4\. `plans/01-security-and-privacy-hardening.md`

## Objective

Improve the application's security without changing its privacy-first operating model or breaking the DNS engine.

### SEC-01: Establish a threat model

**Priority:** P0

Document the threats the application is designed to resist.

Threat categories:

- Malformed or hostile DNS packets.
- Malicious DNS responses.
- Forged upstream responses and alias poisoning.
- Malicious or compromised applications on the device.
- Unexpected network changes.
- Corrupted local settings or reputation files.
- Compromised third-party dependencies.
- Unauthorized access to private application data.
- Accidental disclosure through logs or crash reports.
- Tampered or incorrectly signed release artifacts.

For each threat, document the attack surface, existing mitigations, remaining risks, and corresponding tests.

**Acceptance criteria**

- Every network-facing component has an identified trust boundary.
- Every persisted data type has a documented protection strategy.
- All critical findings have an owner and a remediation test.
- No claim of complete security is made solely from passing unit tests.

### SEC-02: Harden DNS packet processing

**Priority:** P0

Inspect the DNS parser, packet validation, response handling, and UDP-to-TCP fallback.

Required checks:

1. Validate packet lengths before reading fields.
2. Validate DNS header fields and question counts.
3. Bound the number of questions, records, and compression-pointer traversals.
4. Reject invalid compression pointers and out-of-bounds offsets.
5. Prevent integer overflow during length calculations.
6. Validate upstream responses against the corresponding outstanding query.
7. Reject unexpected source addresses and ports.
8. Validate response identifiers and relevant question fields.
9. Bound response sizes and parser resource consumption.
10. Ensure malformed responses cannot poison persistent learning or caches.

Add fuzz tests for malformed packets and property-based tests for parser invariants.

**Important:** Preserve standards-compliant handling of legitimate DNS responses, including truncated UDP responses that require TCP fallback.

**Acceptance criteria**

- Malformed inputs cannot crash the service or cause unbounded allocation.
- Invalid responses cannot enter the normal cache or learning pipeline.
- Valid responses continue to pass existing regression tests.
- Parser failures do not expose raw domains in diagnostic messages.

### SEC-03: Review VPN routing and lifecycle

**Priority:** P0

Inspect `UnblockerVpnService.kt`, `VpnConfiguration.kt`, and the associated lifecycle and resolver components.

Verify:

- VPN consent is respected.
- Protection state is not reported as running before establishment succeeds.
- Repeated start and stop requests are handled safely.
- Shutdown closes sockets, file descriptors, and coroutine scopes.
- Old VPN sessions cannot modify the state of a newer session.
- Resolver sockets are protected from routing back through the VPN.
- Wi-Fi-to-mobile and mobile-to-Wi-Fi transitions are handled correctly.
- Network changes invalidate stale resolver and cache state where necessary.
- Revoked consent and failed establishment produce truthful status.
- Boot restoration follows saved user intent and Android restrictions.

Preserve the existing split-tunnel design.

**Acceptance criteria**

- No duplicate forwarding workers remain after repeated start/stop cycles.
- No stale session can overwrite the current session's state.
- Failed establishment does not display false protection status.
- No unexpected full-device routing is introduced.

### SEC-04: Protect local data

**Priority:** P0

Inspect `PrivateEvidenceStore.kt`, rule storage, Android Keystore integration, and reset behavior.

Required work:

- Verify HMAC identifiers are generated using the intended device-specific key.
- Confirm raw domains are not persisted in reputation or rule snapshots.
- Bound snapshot size and entry counts before parsing.
- Validate schema versions and reject unsupported records safely.
- Test truncated, corrupted, and unexpectedly large files.
- Use atomic replacement for snapshots.
- Prevent concurrent writers from corrupting state.
- Verify temporary files do not contain unnecessary plaintext.
- Confirm reset operations delete all applicable snapshots and legacy files.
- Verify private data remains excluded from Android backup.
- Document key invalidation and recovery behavior.

Do not add Room or SQLite simply for architectural fashion. The current bounded local-store approach is appropriate if it continues to satisfy correctness, privacy, and performance requirements.

**Acceptance criteria**

- Corrupted persistence cannot disable the basic filtering engine.
- Recovery does not require uploading data.
- Reset operations behave consistently across restart.
- Storage failure does not silently weaken filtering or leak sensitive data.

### SEC-05: Audit dependencies and Android components

**Priority:** P1

Review:

- Gradle dependency versions.
- Dependency verification metadata.
- Dependency update policy.
- Android manifest permissions.
- Exported activities, services, and receivers.
- PendingIntent mutability and intent handling.
- WebView usage, if present.
- Debug-only code and logging.
- Release build configuration.
- GitHub Actions permissions and pinned action revisions.

Check whether every permission is necessary. Remove only permissions proven unnecessary after inspecting the implementation.

Do not weaken Android's VPN or foreground-service requirements to simplify the application.

### SEC-06: Eliminate accidental information disclosure

**Priority:** P0

Search the application and build configuration for:

- `Log.d`, `Log.i`, `Log.w`, and `Log.e`.
- `println` and exception-message logging.
- Analytics and crash-reporting integrations.
- Network calls outside the documented DNS forwarding path.
- Domain-bearing notifications.
- Debug endpoints.
- Domain-bearing persistent files.

Review release and debug builds separately.

**Acceptance criteria**

- DNS domains and raw packets are not written to general logs.
- Error messages identify the failure category without exposing sensitive input.
- No telemetry or cloud service is introduced.
- Release builds contain no unintended debugging facilities.

### SEC-07: Validate release integrity

**Priority:** P1

- Verify the signing certificate against the expected production identity.
- Keep signing credentials out of the repository.
- Verify artifact checksums and signatures.
- Ensure release workflows do not silently overwrite published assets.
- Confirm that source tags and application versions agree.
- Verify that dependency and dataset provenance is documented.

### SEC-08: Security regression suite

Add or expand tests for:

- Malformed DNS messages.
- Forged responses.
- CNAME poisoning.
- Oversized inputs.
- Persistence corruption.
- Keystore failures.
- Permission revocation.
- Repeated lifecycle transitions.
- Unexpected network changes.
- Release manifest and signing configuration.

### Completion gate

Do not mark security hardening complete until all critical findings have been resolved or explicitly accepted with documented risk.

```

---

# 5. `plans/02-performance-battery-and-network.md`

## Objective

Make protection efficient during normal use, idle periods, network changes, and high DNS-query volume.

The goal is not merely to make the interface feel fast. The DNS engine must remain efficient while protection is active in the background.

### PERF-01: Establish a reproducible baseline

Measure the existing implementation before changing it.

Collect:

- CPU usage while protection is idle.
- CPU usage during ordinary browsing.
- CPU usage during high DNS-query volume.
- Java and native memory consumption.
- Allocation rate and garbage-collection frequency.
- DNS forwarding latency.
- DNS cache hit rate.
- UDP-to-TCP fallback frequency.
- Packet-processing throughput.
- Battery consumption during controlled tests.
- Data transferred by the application.
- Startup and shutdown latency.

Record Android version, device model, network type, battery conditions, test duration, and source commit.

Use Android Studio Profiler, Android system tracing, and appropriate battery measurement tools.

Do not publish battery-saving or latency percentages without reproducible measurements.

### PERF-02: Protect the DNS hot path

**Priority:** P0

Review `UnblockerVpnService.kt`, `RuleSet.kt`, `RuleSetHolder.kt`, and the filtering engine.

Requirements:

- Keep packet classification allocation-light.
- Avoid disk writes during packet classification.
- Avoid blocking locks on the forwarding path.
- Avoid repeated domain normalization.
- Reuse immutable rule structures where safe.
- Keep rule lookups bounded.
- Prevent expensive heuristic analysis from blocking DNS forwarding.
- Ensure asynchronous persistence has bounded queues and defined failure behavior.
- Prevent overload from creating unbounded coroutine jobs.

Do not introduce lock-free structures without evidence that they improve performance and preserve correctness.

### PERF-03: Optimize caching

Verify:

- DNS response TTL handling.
- Cache expiration.
- Cache size limits.
- Cache invalidation after network changes.
- Correct handling of negative responses.
- Separation of cache entries that differ in relevant query properties.
- Prevention of forged-response cache poisoning.

Do not cache a response longer than permitted by the applicable DNS semantics and the application's safety limits.

### PERF-04: Reduce unnecessary background work

The existing project documents a six-hour local regression check while protection is enabled.

Review its scheduling and execution cost.

Requirements:

- Do not run protection-related recurring work when protection is disabled.
- Avoid continuous polling when Android network callbacks or lifecycle events suffice.
- Do not run periodic UI refresh loops when the screen is hidden.
- Avoid keeping unnecessary wake locks.
- Do not start multiple equivalent background jobs.
- Cancel work promptly when protection stops.
- Avoid repeated initialization of immutable rule data.

Retain required health checks and recovery behavior.

### PERF-05: Minimize network overhead

- Do not add remote analytics or model requests.
- Do not introduce automatic blocklist downloads.
- Reuse existing resolver discovery and DNS forwarding mechanisms where safe.
- Avoid duplicate upstream requests where a valid cached result can be reused.
- Avoid aggressive retries during resolver failure.
- Use bounded exponential backoff for eligible retryable failures.
- Do not silently substitute an unrelated public resolver.
- Verify that malformed or blocked requests do not trigger unnecessary upstream traffic.

The application cannot eliminate the network traffic required to resolve allowed DNS queries.

### PERF-06: Optimize UI rendering

- Keep expensive calculations out of composable functions.
- Use stable keys in lazy lists.
- Avoid rebuilding large lists on every DNS event.
- Collect state with lifecycle-aware APIs.
- Separate rapidly changing status from static screen content.
- Throttle nonessential visual updates.
- Avoid continuously animated graphs.
- Load complex illustrations only where needed.
- Respect system reduced-motion preferences.

DNS events should not trigger unnecessary full-screen recompositions.

### PERF-07: Physical-device test matrix

Test at least:

1. Protection enabled while idle.
2. Protection enabled during ordinary browsing.
3. Sustained DNS-query traffic.
4. Wi-Fi to mobile-data transition.
5. Mobile data to Wi-Fi transition.
6. Temporary loss of internet connectivity.
7. Resolver unavailability.
8. Screen-off operation.
9. Repeated app open and close.
10. Repeated protection start and stop.
11. Low-memory conditions.
12. Low-battery conditions.
13. Extended operation over 24 hours.

Use representative devices and Android versions, including at least one physical device from a manufacturer with aggressive background restrictions.

### PERF-08: Acceptance criteria

The final thresholds must be based on the initial baseline and device capabilities.

Before release:

- No unbounded queue or memory growth is observed.
- No persistent high-CPU loop occurs while idle.
- Background tasks stop when no longer needed.
- DNS forwarding remains correct under load.
- Network transitions do not cause persistent forwarding failures.
- Battery results are documented with reproducible methodology.
- Any performance regression is investigated rather than hidden by an arbitrary threshold.
```

---

# 6\. `plans/03-ui-ux-motion-skeuomorphism.md`

## Objective

Redesign the Compose interface around a coherent visual system while preserving all existing features and their underlying behavior.

## 6.1 Design direction

Use the requested references as sources of design principles, not as unrelated visual styles mixed together.

| Reference | Intended use |
| --- | --- |
| Motion Primitives | Consistent transitions, layout motion, and restrained interactive animation. |
| Haikei | Locally generated or bundled abstract shapes, gradients, and decorative backgrounds. |
| Real-time colors | Adaptive palettes, including Android dynamic color support where available. |
| Godly | High-quality visual inspiration, composition, hierarchy, and interface polish. |
| Replit Agent | Structured implementation workflow, component iteration, and rapid design prototyping. |
| Skeuomorphism | Tactile controls, layered surfaces, subtle depth, physical affordances, and meaningful feedback. |

References: Motion Primitives, Haikei, Godly, and Replit.

**Architectural rule:** These references must not introduce mandatory cloud services, runtime asset downloads, or dependencies that compromise the application's local-only privacy model.

## 6.2 UI-01: Create design tokens

Create a central design system for:

- Colors.
- Typography.
- Spacing.
- Shapes.
- Borders.
- Elevation.
- Animation durations.
- Animation easing.
- Touch-target dimensions.
- Icon sizes.
- Light and dark themes.

Suggested Compose structure:

```
app/src/main/java/com/unblocker/app/ui/
├── theme/
│   ├── Color.kt
│   ├── Type.kt
│   ├── Shape.kt
│   ├── Spacing.kt
│   ├── Motion.kt
│   └── Theme.kt
├── components/
│   ├── ProtectionControl.kt
│   ├── StatusSurface.kt
│   ├── TactileCard.kt
│   ├── FilterToggle.kt
│   ├── MetricDisplay.kt
│   ├── WarningBanner.kt
│   └── EmptyState.kt
└── screens/
    ├── HomeScreen.kt
    ├── LogsScreen.kt
    ├── RulesScreen.kt
    └── SettingsScreen.kt
```

This is a proposed organization. Reuse existing files and components where that avoids unnecessary churn.

## 6.3 UI-02: Skeuomorphic visual system

Use restrained skeuomorphism rather than heavy, decorative effects.

Recommended principles:

- Slightly raised primary controls.
- Pressed states with subtle inset shadows or tonal changes.
- Layered cards with clear depth relationships.
- Carefully chosen corner radii.
- Tactile filter switches.
- Subtle highlights and edge treatments.
- High-contrast status labels.
- A clear visual distinction between active, inactive, starting, and failed states.

Avoid:

- Excessive gradients.
- Large blurred shadows.
- Multiple competing surface effects.
- Tiny controls.
- Decorative elements that resemble actionable controls.
- Animations that obscure protection state.

Skeuomorphic effects must be implemented using efficient native Compose drawing and standard components wherever possible.

## 6.4 UI-03: Main dashboard redesign

The dashboard should answer three questions immediately:

1. Is protection active?
2. Is DNS filtering working?
3. Does the user need to take action?

Suggested hierarchy:

1. Protection status and primary control.
2. Current network and resolver status.
3. Private DNS bypass warning when applicable.
4. Filtering activity and blocked-query statistics.
5. Active filtering options.
6. Shortcuts to rules, logs, and settings.

Keep the main protection action prominent.

Do not make the user navigate through multiple screens to discover whether protection is active or why it is not working.

## 6.5 UI-04: Motion design

Use animations to explain state changes.

Examples:

- Press feedback for the primary protection control.
- A short transition from `STOPPED` to `STARTING`.
- A restrained transition to `RUNNING` after successful establishment.
- Animated expansion of diagnostic details.
- Smooth navigation between screens.
- Small state transitions for filter toggles.

Motion must follow actual application state. Never animate a button into a success state before the service confirms that protection is active.

Respect reduced-motion preferences. When animations are disabled, status changes must remain understandable and immediate.

Avoid infinite decorative animations, continuously moving backgrounds, and expensive per-frame effects.

## 6.6 UI-05: Adaptive colors

Use Android dynamic color where supported, with a carefully designed fallback palette.

Requirements:

- Support light and dark themes.
- Preserve contrast in every theme.
- Keep warning, error, and success states semantically consistent.
- Avoid random color changes.
- Avoid expensive color calculations during every recomposition.
- Persist user theme preferences locally.
- Ensure adaptive colors never obscure security warnings.

Use Haikei-inspired assets as static local resources or inexpensive deterministic drawing operations.

Do not download new decorative assets at runtime.

## 6.7 UI-06: Preserve functionality

Before migrating screens, record the existing behavior of:

- Protection start and stop.
- VPN permission handling.
- Notification permission handling.
- Adult-content filtering.
- Custom allow and block rules.
- Live filter changes.
- Learned-reputation reset.
- Rule clearing.
- Logs and query-decision displays.
- Private DNS warnings.
- Startup preferences.
- Settings persistence.
- Error and recovery states.

The UI must call the existing domain logic and state-management mechanisms.

Do not rewrite DNS processing as part of the visual redesign.

Do not remove a feature because it is difficult to accommodate in the new layout.

## 6.8 UI-07: Accessibility

Verify:

- Screen-reader labels for every actionable control.
- Semantic status descriptions.
- Keyboard and accessibility navigation.
- Sufficient text and icon contrast.
- Appropriate touch-target sizes.
- Text scaling without clipping.
- Reduced-motion support.
- Clear error messages.
- Usable layouts on narrow screens.
- Localization-safe text sizing.

Do not communicate protection state through color alone.

## 6.9 UI-08: Visual regression testing

Capture baseline screenshots before migration.

Compare:

- Home screen.
- Logs screen.
- Rules screen.
- Settings screen.
- Permission prompts.
- Private DNS warning.
- Starting, running, stopped, and error states.
- Light and dark themes.
- Small and large text settings.

Screenshots alone are insufficient. Add behavioral tests to ensure that the redesigned controls still trigger the correct operations.

## 6.10 UI acceptance criteria

- All existing features remain accessible.
- Protection status reflects the real service state.
- No new runtime network dependency is introduced.
- The interface remains responsive during high DNS activity.
- Reduced-motion and accessibility requirements pass.
- No persistent animation runs unnecessarily.
- The previous interface can be restored if the migration introduces regressions.

```

---

# 7. `plans/04-regression-and-quality-assurance.md`

## Objective

Make it difficult for security, performance, and UI changes to break existing functionality.

### QA-01: Establish the feature inventory

Before making changes, map every user-visible feature to:

- Its UI component.
- Its ViewModel or state holder.
- Its domain use case.
- Its persistence mechanism.
- Its service or network dependency.
- Its existing tests.

Mark every feature as:

- Verified.
- Partially verified.
- Not yet tested.
- Known limitation.

Do not classify a feature as verified merely because a corresponding button exists.

### QA-02: Maintain a baseline

Run the repository's documented build and test commands:
```

./gradlew testDebugUnitTest lintDebug assembleDebug ./gradlew connectedDebugAndroidTest

python -m unittest discover  -s tools/filter-compiler  -p 'test\_\*.py' -v

```

Use a configured Android emulator or test device for instrumentation tests.

Record:

- Git commit.
- JDK and Android SDK versions.
- Test counts and failures.
- Lint results.
- APK build result.
- Device and emulator versions.
- Known failing tests.

These commands are the project's documented baseline, not a claim that they have been executed during this audit.

### QA-03: DNS regression tests

Verify:

- Known blocked domains are blocked.
- Allowed domains remain available.
- Allow rules take precedence where documented.
- Disabling a filter takes effect immediately.
- Invalid DNS packets are rejected safely.
- DNS-over-TCP fallback works.
- IPv4 and IPv6 responses are correct.
- Cache expiration is correct.
- Network changes refresh resolver state.
- CNAME responses cannot poison persistent learning.
- Resolver failures do not trigger hidden fallback behavior.

### QA-04: Lifecycle tests

Test:

- Start.
- Stop.
- Start twice.
- Stop twice.
- Rapid start/stop sequences.
- Process death.
- Device reboot.
- Permission revocation.
- Network loss and recovery.
- Service establishment failure.
- Service teardown during outstanding requests.

### QA-05: UI integration tests

For each screen, verify both appearance and behavior.

Examples:

- Pressing the protection control requests the correct service transition.
- A failed transition produces an error state.
- Changing a filter updates actual filtering behavior.
- Clearing rules changes the persistent rule state.
- Resetting learning removes the relevant stored evidence.
- Logs do not reveal information that the privacy model forbids.
- Warnings appear when their underlying conditions are detected.

### QA-06: Privacy regression tests

Verify that:

- No telemetry endpoint is introduced.
- No new domain-bearing persistent storage is introduced.
- No new domain-bearing log statements are introduced.
- Private data remains excluded from backup.
- No cloud synchronization is added.
- No hidden resolver is introduced.
- No UI feature silently exports user data.

### QA-07: Release gates

Every pull request affecting application behavior must pass the relevant automated checks.

Changes to DNS processing, storage, or VPN lifecycle require focused regression tests.

Changes to UI components require behavioral and accessibility tests.

Changes affecting performance require before-and-after measurements.

Changes to release configuration require signing and artifact-integrity verification.

### QA-08: Rollback

Keep UI, engine, and persistence changes in separate reviewable commits whenever practical.

If a change causes a regression:

1. Identify the failing invariant.
2. Reproduce the problem.
3. Add a regression test.
4. Fix or revert the change.
5. Re-run the relevant suite.
6. Record the cause and resolution.
```

---

# 8\. `plans/05-dns-accuracy-and-bypass-coverage.md`

## Objective

Improve blocking accuracy and explain filtering limitations without making the network engine more invasive.

### DNS-01: Expand the evaluation dataset

Build a versioned, labeled dataset containing:

- Advertising endpoints.
- Tracker endpoints.
- Legitimate websites.
- Authentication services.
- Operating-system connectivity endpoints.
- Software update services.
- Developer tools.
- Content delivery networks.
- Educational and healthcare domains.
- Domains used by the application's own test scenarios.

Record dataset provenance and licensing.

Separate training or rule-development examples from the evaluation corpus to avoid misleading results.

### DNS-02: Measure classification quality

Report:

- True positives.
- False positives.
- True negatives.
- False negatives.
- Precision.
- Recall.
- Results by category.
- Results by dataset revision.

Preserve the existing zero-false-positive gate for necessary domains, while expanding the set of necessary domains tested.

Do not optimize recall at the expense of breaking legitimate services.

### DNS-03: Review heuristic learning

Inspect the local learner and heuristic scorer.

Verify:

- Weak evidence does not independently authorize blocking.
- Independent observation windows work as intended.
- Corroborating signals are required where specified.
- Evidence expires according to documented policy.
- Repeated observations do not cause uncontrolled score inflation.
- Trusted alias changes cannot poison persistent reputation.
- User allow rules override blocking where documented.
- Learning remains local.
- Learning failures do not interrupt basic filtering.

### DNS-04: Document bypass scenarios

Test and document:

- Android Private DNS in strict mode.
- Browser DNS-over-HTTPS.
- Application-specific encrypted DNS.
- Direct-IP connections.
- Previously cached DNS results.
- DNS queries outside the intercepted interface.
- Network changes while protection is active.

When a bypass is detected, explain what is happening and what the user can do.

Do not claim complete coverage when the traffic is not visible to the local DNS filter.

### DNS-05: Resolver correctness

Verify:

- Resolver discovery uses the active network configuration.
- Invalid and duplicate resolver addresses are excluded.
- VPN and loopback addresses are handled correctly.
- Resolver failures are reported accurately.
- DNS-over-TCP fallback is bounded and correct.
- Network changes do not leave stale resolver state.
- No public resolver is substituted silently.

### DNS-06: Safety protections

Preserve the existing never-block policy and expand tests for critical infrastructure.

Every new rule source must have documented provenance and a reproducible compilation process.

Review broad wildcard-like or lexical rules for false-positive risks.

### DNS acceptance criteria

- The expanded corpus passes the agreed safety gates.
- Every new rule source has documented licensing and provenance.
- Filtering behavior remains deterministic where required.
- Encrypted-DNS limitations are communicated honestly.
- No new cloud dependency is introduced.

```

---

# 9. `plans/06-release-readiness-and-documentation.md`

## Objective

Make the source code, documentation, builds, and published APKs consistent and verifiable.

### REL-01: Reconcile version information

Inspect:

- `app/build.gradle.kts`.
- Root build configuration.
- README release references.
- Git tags.
- GitHub Releases.
- Release workflow configuration.
- Published APK metadata.

Establish one authoritative source for the application version.

Document the relationship between:

- Source commit.
- Application version name.
- Application version code.
- Release tag.
- APK checksum.
- Signing certificate.

### REL-02: Verify build reproducibility

Document the supported build environment, including:

- JDK version.
- Android SDK version.
- Gradle wrapper version.
- Required environment variables.
- Required local configuration.
- Unit-test commands.
- Instrumentation-test commands.
- Release-build procedure.

Keep machine-specific configuration and secrets out of source control.

### REL-03: Verify release signing

- Confirm the expected production signing identity.
- Keep private signing keys outside the repository.
- Ensure release jobs fail if required signing configuration is absent.
- Verify the resulting APK signature.
- Verify published artifact checksums.
- Do not replace existing release assets silently.
- Do not publish a debug-signed APK as a production release.

### REL-04: Review dependencies

- Verify dependency integrity.
- Review dependency update alerts.
- Document third-party licenses.
- Remove dependencies only after confirming they are unused.
- Avoid adding animation or UI libraries without a concrete need.
- Avoid unnecessary network-capable SDKs.

### REL-05: Update documentation

Keep the following consistent:

- README.
- Privacy policy.
- Permissions documentation.
- DNS filtering limitations.
- Build instructions.
- Performance results.
- Coverage baseline.
- Improvement tracker.
- Release notes.

Clearly distinguish implemented features, automated-test evidence, physical-device evidence, and outstanding work.

### REL-06: Release acceptance

A release candidate must pass:

1. Unit tests.
2. Static analysis.
3. Instrumentation tests.
4. DNS regression tests.
5. Privacy regression checks.
6. Build and signing verification.
7. UI accessibility checks.
8. Physical-device lifecycle tests.
9. Performance and battery acceptance tests.
10. Documentation consistency checks.
```

---

# 10\. Additional implementation rules

These rules apply to every plan file.

## 10.1 Preserve existing functionality through an explicit migration matrix

Before redesigning the interface, create a table in the repository mapping every existing feature to its current implementation and its new UI destination.

For example:

| Existing capability | Migration rule | Required verification | |---|---|---| | Protection control | Replace appearance, not service semantics. | Start/stop instrumentation tests. | | DNS filtering | Preserve existing engine. | DNS regression suite. | | Custom allow/block rules | Preserve rule precedence and persistence. | Rule integration tests. | | Private learning | Preserve local processing and retention rules. | Persistence and privacy tests. | | Private DNS warning | Improve presentation without hiding the warning. | Bypass-condition tests. | | Logs | Improve readability without adding sensitive logging. | Privacy and UI tests. | | Startup restoration | Preserve lifecycle behavior. | Reboot and lifecycle tests. | | Settings | Retain existing settings and defaults unless explicitly approved. | Settings persistence tests. |

The matrix must be expanded to include every actual feature discovered during the source audit.

## 10.2 Keep the application local-first

All of the following should remain on the device:

- Filtering decisions.
- Heuristic learning.
- User-defined rules.
- Reputation storage.
- Settings.
- Local diagnostics.
- Health checks.
- Theme preferences.
- UI state that does not require remote services.

An important distinction: **on-device processing does not mean zero network traffic**. Resolving an allowed domain requires a DNS resolver, and that resolver can see the query. The goal is to avoid unnecessary traffic and prevent disclosure to additional parties.

## 10.3 Do not compromise performance for visual effects

A beautiful interface is not worth an always-running animation or an expensive rendering pipeline.

Use static assets, simple drawing operations, bounded animations, and lifecycle-aware state collection.

The dashboard should remain responsive even when the DNS engine is busy.

## 10.4 Avoid unnecessary architecture rewrites

Do not introduce a new database, dependency-injection framework, networking stack, or state-management framework simply to modernize the code.

First inspect the current implementation. Change architecture only when there is a demonstrated correctness, security, maintainability, or performance benefit.

## 10.5 Use a phased rollout

Recommended sequence:

1. Record baseline tests and measurements.
2. Complete the threat model and source-level security review.
3. Fix confirmed critical security defects.
4. Expand DNS and privacy regression tests.
5. Establish physical-device performance baselines.
6. Implement the design tokens and reusable components.
7. Migrate the dashboard.
8. Migrate remaining screens individually.
9. Run the full regression suite.
10. Perform physical-device and release acceptance testing.

Do not merge all ten stages into one large change.

---

# 11\. Final acceptance checklist

Before considering the project complete, verify every item below.

### Security and privacy

- [ ]DNS parsing and response validation have been reviewed.
- [ ]VPN lifecycle and routing have been reviewed.
- [ ]Local storage and Keystore behavior have been tested.
- [ ]No unintended telemetry or cloud dependency exists.
- [ ]Sensitive information is not exposed through logs.
- [ ]Android permissions and exported components have been reviewed.
- [ ]Release signing and artifact integrity have been verified.
- [ ]Known DNS bypasses are documented and tested.

### Performance and reliability

- [ ]CPU, memory, and battery baselines are recorded.
- [ ]DNS forwarding remains correct under load.
- [ ]Memory and work queues remain bounded.
- [ ]Background work stops when it is no longer required.
- [ ]Wi-Fi/mobile transitions have been tested.
- [ ]Extended physical-device testing has been completed.
- [ ]No unverified performance claims are published.

### UI and accessibility

- [ ]Design tokens and reusable Compose components are established.
- [ ]Skeuomorphic styling is consistent and restrained.
- [ ]Motion reflects actual application state.
- [ ]Reduced-motion preferences are respected.
- [ ]Dynamic colors and light/dark themes are tested.
- [ ]Screen readers and text scaling work correctly.
- [ ]Every existing feature remains available.
- [ ]Visual and behavioral regression tests pass.

### Quality and release

- [ ]Existing features have been inventoried.
- [ ]Unit tests and instrumentation tests pass.
- [ ]DNS accuracy has been evaluated on a larger dataset.
- [ ]Version information is consistent across source and releases.
- [ ]Production signing has been verified.
- [ ]Documentation reflects the implementation actually shipped.

---

## 12\. What has been completed in this audit?

To be precise about the deliverable:

- I reviewed the public main-branch README and the available improvement, coverage, DNS bypass, and privacy documentation.
- I identified the major verification gaps and documented the recommended solutions.
- I prepared the contents and implementation requirements for **seven proposed Markdown plan files**.
- I defined priorities, testing requirements, migration constraints, and release acceptance criteria.

**I have not created or committed these files to GitHub.** I do not have an active repository-write operation available in this session, and I will not claim otherwise. The Markdown above is ready to be added to the repository, but the existing source code has not been modified.

Repository: [https://github.com/pkgtm2419/un-blocker](<https://github.com/pkgtm2419/un-blocker>)

The next engineering step should be to add these plans, inspect the actual Kotlin implementation against them, and establish a passing baseline before changing the UI or DNS engine. That sequence offers the best chance of improving the application without sacrificing its existing features, privacy model, reliability, or performance.









# Un-Blocker: Fixing the 686,200-Rule Memory and Performance Bottleneck

**My recommendation is to fix the rule engine before adding more features or redesigning the UI.** A DNS blocker must be able to handle large blocklists without exhausting Android memory, slowing DNS responses, or consuming excessive battery.

The proposed diagnosis is plausible, but some of its claims need to be verified against the actual implementation.

For example, 686,200 rules do not automatically imply excessive memory usage or slow packet processing. The real cost depends on how the rules are represented, how many copies are retained, how matching works, and how updates are performed.

Similarly, `android:largeHeap="true"` increases the application's available heap on supported devices; it does not fix inefficient data structures, eliminate garbage collection, or guarantee protection against `OutOfMemoryError`.

The right solution is to measure the existing implementation, optimize its data representation and lookup algorithm, and establish explicit memory and latency budgets.

## 1\. Evaluate the proposed solutions

| Proposed solution | Assessment | Recommendation |
| --- | --- | --- |
| Disable redundant lists | Useful immediate mitigation | Implement list selection and redundancy analysis without requiring users to sacrifice necessary protection. |
| Consolidate blocklists | Useful, but source selection matters more than the number of lists | Deduplicate rules and preserve source attribution and allowlist semantics. |
| Radix tree or trie | Potentially excellent for domain suffix matching | Benchmark a compact representation against the existing rule engine. |
| Bloom filter | Very memory-efficient for membership checks | Use as an optional prefilter, not as the sole authoritative rule engine. |
| Native C++ or Rust | Can reduce managed-heap pressure, but adds complexity | Consider only if a compact managed representation cannot meet measured targets. |
| `android:largeHeap="true"` | Can postpone heap exhaustion | Do not treat it as the fix; evaluate removing it after measuring the optimized implementation. |

There is one important correction to the proposed radix-tree explanation.

DNS blocklist entries commonly represent domains such as:

- `ads.example.com`
- `tracker.example.com`
- `analytics.example.com`

These share a **suffix**, such as `example.com`. A conventional character trie naturally shares prefixes, not suffixes. For domain matching, a reverse-label trie is a natural fit: split the domain into labels and traverse them from right to left.

For example:

```
example.com
├── ads
├── tracker
└── analytics
```

A compact reverse-label trie can efficiently represent these rules and their subdomains without retaining a separate full domain string for every entry.

However, it is not automatically the smallest or fastest possible representation. A carefully designed flat array, compressed trie, or compiled binary rule set may perform better. We should benchmark actual implementations rather than assume one data structure wins.

## 2\. First, establish what is actually causing the problem

Before choosing a new data structure, inspect the code responsible for loading and matching the 42 filter lists.

The audit should focus on these areas:

| Component | What to investigate |
| --- | --- |
| Filter-list importer | Does it load entire files into memory before processing them? |
| Rule compiler | Does it deduplicate domains before creating runtime structures? |
| `RuleSet.kt` | Are rules retained as individual strings or multiple collections? |
| `RuleSetHolder.kt` | Are old and new rule sets retained simultaneously during updates? |
| Rule matching | Does each DNS query scan every rule, or use indexed lookups? |
| Persistence | Are both the source lists and compiled rules loaded into memory? |
| UI | Does Compose observe or render hundreds of thousands of individual rules? |
| Updates | Are rule compilation and large allocations performed on the main thread? |
| Filtering semantics | Are exact domains, subdomains, exceptions, and wildcard rules handled correctly? |

These are investigation targets, not confirmed defects in the current source code.

### Why duplicate representations matter

Suppose the application keeps all of the following in memory:

1. Original text from 42 filter lists.
2. Parsed domain strings.
3. A `HashSet<String>` for deduplication.
4. A second collection for runtime matching.
5. A separate representation for the UI.
6. An old rule set while the new one is being compiled.

The memory consumption could be substantially larger than the size of the original text files.

The problem becomes particularly serious if updates create multiple copies or if the application retains temporary collections after compilation.

**The first optimization should be to eliminate unnecessary representations, not immediately introduce JNI or rewrite the engine in Rust.**

### Measure four separate costs

Record these measurements with the same rule dataset and device:

- **Loading:** Time and peak memory required to import the lists.
- **Steady state:** Memory retained after loading and garbage collection.
- **Lookup:** Time to evaluate a DNS query against the compiled rules.
- **Update:** Peak memory while rebuilding and replacing the rules.

Also record managed heap, native memory, allocation rate, GC pauses, DNS latency percentiles, and CPU usage.

Measure both the idle state and realistic DNS traffic.

This will establish whether the main problem is loading, storage, lookup complexity, update-time duplication, or some combination of them.

---

## 3\. Recommended architecture for 700,000+ rules

My preferred starting point is a **compiled, immutable, compact rule engine in Kotlin**, with a reverse-label trie or another benchmark-proven indexed representation.

A Bloom filter can be added later if it measurably improves performance.

### Layer 1: Import and normalize rules

Process filter lists incrementally rather than reading every source file into a large in-memory string.

Recommended pipeline:

```
Filter-list sources
        |
        v
Incremental parser
        |
        v
Rule normalization
        |
        v
Deduplication and conflict resolution
        |
        v
Compact rule compiler
        |
        v
Immutable runtime rule set
        |
        v
DNS query evaluation
```

Requirements:

- Bound input file sizes and total rule counts.
- Reject malformed entries safely.
- Normalize domain names consistently.
- Handle case normalization and internationalized domains correctly.
- Deduplicate equivalent rules.
- Preserve exception and allowlist semantics.
- Preserve source attribution for diagnostics.
- Keep the previous working rule set active until the replacement is ready.
- Perform compilation off the main thread.
- Avoid retaining intermediate collections after compilation.

If filter lists are stored locally, the compiler should operate on local data. If the application already supports downloading lists, network access should remain an explicit, documented feature rather than becoming a hidden requirement.

### Layer 2: Compile into a compact representation

Instead of retaining hundreds of thousands of independently allocated strings, store shared domain labels or encoded nodes in compact arrays.

For example:

```
CompiledRuleSet
├── Node array
├── Edge array
├── Rule flags
├── Exception metadata
└── Optional source identifiers
```

Use primitive arrays where appropriate to avoid unnecessary object allocation.

A reverse-label trie can support efficient matching:

```
analytics.example.com
         |
         v
       com
         |
         v
      example
         |
         v
      analytics
```

The engine can determine whether a domain matches an exact rule or an applicable parent-domain rule while traversing its labels.

The compiler must preserve the semantics of the existing engine. An exact-domain rule and a rule that also covers all subdomains are not necessarily interchangeable.

### Layer 3: Keep DNS processing predictable

The runtime path should:

1. Validate the DNS query.
2. Extract and normalize the queried domain.
3. Apply any required allowlist and rule-precedence checks.
4. Evaluate the compiled rule set.
5. Produce the appropriate response or continue forwarding.
6. Record only the minimal information required for local diagnostics and learning.

Avoid disk access, rule compilation, large temporary allocations, and expensive UI updates in this path.

The runtime rule set should be immutable. When a new set is ready, swap it into service safely rather than modifying a shared structure while queries are being processed.

This approach can also reduce lock contention and prevent partially updated rules from becoming visible.

---

## 4\. Should you use a Bloom filter?

**Yes, potentially—but not as the only rule-matching mechanism.**

A Bloom filter is a probabilistic data structure that answers whether an item is _possibly present_ or _definitely absent_.

It offers:

- Compact memory usage.
- Fast membership checks.
- No need to retain a Java string for every indexed domain.
- Predictable memory allocation when configured in advance.

However, it has a crucial limitation: **a Bloom filter can produce false positives.**

For a privacy application, this means a legitimate domain could be incorrectly classified as blocked if the Bloom filter is the sole authority.

### Recommended hybrid design

```
Incoming DNS query
        |
        v
Validate query
        |
        v
Check explicit allow/exception rules
        |
        v
Bloom filter
   /          \
Absent       Possibly present
  |                |
  v                v
Continue       Exact compiled
normal path    rule evaluation
                   |
             +-----+-----+
             |           |
           Match       No match
             |           |
             v           v
          Block      Allow/forward
```

This diagram illustrates the general approach; the exact placement of exceptions and other rule types must follow the application's established semantics.

The Bloom filter should be introduced only if benchmarks show that it improves the existing indexed matcher. A well-designed trie may already be fast enough without it.

### Illustrative memory calculation

For a Bloom filter containing 686,200 entries, a target false-positive probability of 0.1% would require approximately:

- **9.9 million bits**
- **1.24 MB** for the bit array
- Approximately **10 hash probes** per membership check

These are theoretical estimates, not measurements of the application's performance.

The filter would still need its associated metadata, and an exact rule representation would remain necessary for the hybrid design.

Also, if most queries are for allowed domains, the Bloom filter could be useful as a fast negative check. If nearly every query needs additional rule evaluation anyway, the extra hashing might provide little benefit.

**Recommendation:** Benchmark the compact trie first, then compare it with the trie-plus-Bloom-filter implementation.

---

## 5\. Should you move the rule engine to native C++ or Rust?

Not yet.

Moving the engine to native memory can reduce pressure on the managed JVM heap, but it does not automatically make the application faster or more energy-efficient.

Native implementations introduce additional engineering costs:

- JNI boundary overhead.
- Native memory ownership and cleanup.
- Potential memory leaks outside the JVM heap.
- Native crash risks.
- More complicated debugging.
- Additional build and testing requirements.
- Potential compatibility problems across Android ABIs.
- More difficult integration with Kotlin-based rule updates.

Native memory is not invisible to the device's total memory limits. Android can still terminate a process that consumes too much memory.

A native implementation could make sense if profiling demonstrates that the optimized Kotlin engine still has an unacceptable memory footprint or latency.

If that becomes necessary, Rust is worth evaluating for its memory-safety advantages, although its Android integration and runtime costs must also be measured.

For now, the better sequence is:

1. Remove redundant string and collection representations.
2. Compile the rules into a compact immutable structure.
3. Benchmark lookup performance.
4. Add a Bloom filter only if useful.
5. Reconsider native code only if the measured results justify it.

This avoids turning a data-structure problem into a much larger architectural rewrite.

---

## 6\. What should happen to `android:largeHeap="true"`?

Treat it as a temporary mitigation, not the solution.

First inspect the manifest and determine whether `android:largeHeap="true"` is present and which application component requires it.

Then:

1. Record baseline memory usage.
2. Implement and test the optimized rule representation.
3. Measure the optimized application's peak and steady-state memory.
4. Test rule loading and replacement under realistic memory pressure.
5. Test on lower-memory Android devices.
6. Remove `android:largeHeap="true"` in a controlled change if the application no longer needs it.
7. Run the complete regression suite again.

Do not remove the attribute before confirming that the application can safely operate within the ordinary heap limits of its supported devices.

Also, do not retain it indefinitely just because it prevents a crash on one test device.

---

## 7\. What about the 42 filter lists?

You should provide a way to manage filter-list selection, but I would not make the user manually disable protection sources to compensate for inefficient storage.

There are three separate concerns:

- Number of configured lists.
- Number of unique rules after deduplication.
- Memory and CPU costs of the compiled runtime representation.

Forty-two lists may produce 686,200 entries but substantially fewer unique rules. Conversely, a smaller number of lists can still create a large rule set.

### Recommended filter-list management

Implement these capabilities:

- Show the number of enabled lists.
- Show the raw entry count and unique rule count.
- Report invalid and duplicate entries.
- Identify overlapping lists.
- Display the approximate compiled rule-set size.
- Let users enable and disable individual lists.
- Support curated presets.
- Preserve custom allowlists and blocklists.
- Explain the privacy and compatibility trade-offs of aggressive filtering.

Do not automatically disable lists simply because their entry counts exceed an arbitrary threshold.

### Should you recommend OISD or StevenBlack?

They are reasonable candidates for a curated preset, but their suitability depends on the desired coverage, licensing, update policy, and compatibility requirements.

I would not promise that either will block 99% of ads and trackers without a representative evaluation dataset.

Instead, compare candidate configurations using:

- Unique rule count.
- Compiled memory footprint.
- DNS lookup latency.
- Advertising and tracking detection recall.
- False-positive rate.
- Compatibility with common applications.
- Update size and compilation time.

The default preset should be selected using measured results rather than marketing claims.

One additional privacy consideration: if list updates require internet access, disclose that behavior and keep it separate from ordinary DNS filtering. Do not introduce a remote dependency merely to support the optimized runtime engine.

---

# 8\. Concrete implementation roadmap

I recommend adding a dedicated plan file:

`plans/07-large-blocklist-memory-optimization.md`

The following is the implementation specification I would put into it.

### Phase 1 — Reproduce and profile the issue

**Priority: P0**

- [ ]Verify whether 686,200 represents raw entries or unique rules.
- [ ]Inspect the actual filter-list loading and compilation code.
- [ ]Inspect `RuleSet.kt` and `RuleSetHolder.kt`.
- [ ]Identify duplicate in-memory representations.
- [ ]Measure peak heap usage during loading and replacement.
- [ ]Measure steady-state Java and native memory.
- [ ]Determine whether DNS matching is indexed or linear.
- [ ]Capture GC pauses and DNS latency under load.
- [ ]Reproduce the reported warning on a physical Android device.

**Deliverable:** A profiling report identifying the dominant memory allocations and computational bottlenecks.

### Phase 2 — Remove avoidable memory overhead

**Priority: P0**

- [ ]Parse lists incrementally.
- [ ]Deduplicate before constructing the runtime representation.
- [ ]Eliminate unnecessary retained source strings.
- [ ]Avoid creating redundant `HashSet` and `HashMap` copies.
- [ ]Bound input sizes and compilation memory.
- [ ]Compile rules off the main thread.
- [ ]Release temporary collections after compilation.
- [ ]Prevent UI components from retaining the full rule dataset.

**Deliverable:** A measured reduction in peak and steady-state memory without changing filtering behavior.

### Phase 3 — Implement the compact rule engine

**Priority: P0**

- [ ]Define the existing rule semantics.
- [ ]Implement a compact reverse-label trie or equivalent indexed representation.
- [ ]Preserve exact-domain and subdomain matching.
- [ ]Preserve exception and allowlist precedence.
- [ ]Preserve wildcard handling where supported.
- [ ]Keep the runtime structure immutable.
- [ ]Replace rule sets atomically.
- [ ]Maintain a reference implementation for differential testing.

**Deliverable:** A compact runtime rule engine that produces the same decisions as the reference implementation.

### Phase 4 — Evaluate Bloom filtering

**Priority: P1**

- [ ]Establish the compact trie performance baseline.
- [ ]Prototype a Bloom filter with a documented false-positive target.
- [ ]Verify that false positives never cause unauthorized blocking.
- [ ]Benchmark negative lookups, positive lookups, and realistic mixed workloads.
- [ ]Measure CPU time and energy implications.
- [ ]Keep the Bloom filter only if the results justify its complexity.

**Deliverable:** A documented benchmark comparison and an evidence-based decision.

### Phase 5 — Validate performance and memory

**Priority: P0**

Test at least these configurations:

| Test | Purpose |
| --- | --- |
| 50,000 rules | Small configuration baseline |
| 150,000 rules | Medium configuration |
| 350,000 rules | Large configuration |
| 686,200 rules | Reported workload |
| 1,000,000 rules | Scalability test, if supported by the product's intended limits |

For each configuration, record:

- Raw entry count.
- Unique rule count.
- Peak memory during compilation.
- Steady-state memory.
- Compilation duration.
- Lookup latency at p50, p95, and p99.
- CPU usage.
- GC activity.
- DNS forwarding success rate.
- Battery impact under a reproducible workload.

These are test sizes, not commitments that every device must support one million rules.

### Phase 6 — Reassess large-heap configuration

**Priority: P1**

- [ ]Verify the existing manifest configuration.
- [ ]Repeat all memory tests after optimization.
- [ ]Test supported low-memory devices.
- [ ]Remove `android:largeHeap="true"` if the evidence supports doing so.
- [ ]Verify that no regressions occur during large rule-set updates.

### Phase 7 — Release acceptance

**Priority: P0**

- [ ]All existing DNS regression tests pass.
- [ ]Differential tests confirm equivalent filtering decisions.
- [ ]No unbounded memory growth is observed.
- [ ]Rule-set replacement does not interrupt DNS processing.
- [ ]The application remains responsive during compilation.
- [ ]Memory and latency targets are met on representative devices.
- [ ]Battery results are documented.
- [ ]Existing filtering, learning, and privacy features remain functional.

---

## 9\. How should you define success?

Avoid setting an arbitrary memory target before profiling the existing application.

Instead, establish the baseline and then agree on measurable limits for supported devices.

For example, you could set engineering objectives such as:

- No `OutOfMemoryError` when loading the maximum supported rule set on supported devices.
- No linear scan of all 686,200 rules for every DNS query.
- No unbounded allocation or work queues.
- No expensive compilation on the main thread.
- No persistent DNS latency regression under realistic workloads.
- No additional network traffic merely to perform local filtering.
- No false-positive increase caused by the optimized rule representation.
- No loss of existing rule semantics.
- No persistent high-CPU activity while protection is idle.

These are proposed acceptance requirements. The actual memory and latency budgets should be established using the supported device matrix and baseline measurements.

## 10\. Final recommendation

I would implement this in the following order:

1. **Profile the current engine** to identify the actual source of memory and latency problems.
2. **Remove duplicate strings and intermediate collections.**
3. **Introduce a compact, immutable, indexed rule representation**, preferably a reverse-label trie if benchmarks support it.
4. **Use a Bloom filter only if it produces a measurable improvement.**
5. **Keep native code as a later optimization**, not the starting point.
6. **Reassess****`android:largeHeap="true"`****after measuring the optimized implementation.**
7. **Add the new plan file and regression tests before changing the engine.**

The most important principle is that **686,200 rules should be treated as a scalability requirement, not as a reason to sacrifice filtering coverage**.

A well-designed DNS rule engine should be able to process large blocklists without scanning the entire dataset for each query, retaining multiple redundant copies of every domain, or relying on an enlarged JVM heap as its primary memory-management strategy.

I have not yet verified the exact allocation profile or implemented these changes in the repository. The next step is a source-level inspection of the actual rule compiler and runtime matcher, followed by profiling on the reported workload. That will determine which optimizations offer the greatest benefit with the lowest risk to existing functionality.
