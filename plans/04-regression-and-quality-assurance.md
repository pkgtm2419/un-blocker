# Plan 04: Regression and Quality Assurance

## Objective

Ensure comprehensive verification and prevent regressions across DNS resolution, lifecycle, storage, and UI states.

### QA-01: Regression gates
- All 116+ unit tests in Gradle must pass.
- All Python filter compiler and release policy unit tests must pass.
- Preflight release tag validation must pass.

### QA-02: DNS correctness
- Blocked domains are consistently blocked.
- Legitimate primary domains are never blocked.
- Allowlist rules take precedence over all block rules.
- Negative responses and truncated DNS responses handle TCP fallback properly.

### QA-03: Lifecycle safety
- Service start and stop operations are idempotent.
- Duplicate starts or rapid toggling cannot leave dangling sockets or leaks.
- VPN revocation properly resets UI and halts the background service.
