# Un-Blocker Engineering Improvement Roadmap

Status: Active
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

- Phase 0: Repository inventory and baseline
- Phase 1: Security and privacy hardening
- Phase 2: DNS correctness and bypass coverage
- Phase 3: Performance and battery optimization
- Phase 4: Design system and accessibility
- Phase 5: Incremental UI migration
- Phase 6: Physical-device verification
- Phase 7: Release readiness

Each phase requires documented results before it is considered complete.

## 4. Workstream Status

- [x] SEC: Security and privacy review
- [x] DNS: Filtering correctness and primary domain safety
- [x] PERF: CPU, memory, battery, and responsive upstream routing
- [x] UI: Motion primitives, reduced-motion adaptation, permission readiness
- [x] A11Y: Accessibility verification
- [x] REG: Existing-feature regression suite passing (220+ unit tests)
- [x] REL: Release and signing verification

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
