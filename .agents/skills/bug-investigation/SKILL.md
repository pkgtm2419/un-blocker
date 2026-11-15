---
name: bug-investigation
description: Use when diagnosing crashes, DNS filtering false positives, VPN disconnects, slowdowns, stale logs or non-reproducible Android problems.
---

# Bug Investigation

## Diagnostic loop
1. Capture minimal reproducible steps with synthetic domains, checked-out SHA, Android API/OEM, settings/tier/mode and expected/actual outcome. Never request or publish private browsing logs.
2. Read service and test path; differentiate active runtime module from similar unused component. Locate first causal failure and error propagation.
3. Write a regression that fails before fix, including malformed/concurrent/perms conditions if involved. If no device is available, label device validation NOT RUN.
4. Fix root cause, not a test skip or silent catch. Preserve fail-open decision contract, explicit UI recovery and privacy bounds.
5. Verify targeted case and regression suite plus documented security/device implications.
6. Document symptom, root cause, test result, changed files and updated feature/architecture/behavior guides.

## Example
After a VPN toggle turns off filtering, inspect `QuickStartManager`, `FilteringPreferences` and session state before changing UI button text.

## Mistake
Confusing global DNS resolution failure with the domain rule engine blocking a host.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
