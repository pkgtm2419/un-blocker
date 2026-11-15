# New feature and bug implementation playbook

## Before coding

Create a one-paragraph requirement: user goal, trigger, screens, state, failure handling, privacy and Android version implications. Record existing behavior and tests. Use the applicable Antigravity skill (`feature-implementation`, `bug-investigation`, `dns-protocol-change`, `compose-ui-change`, `privacy-security-review` or `filter-list-update`).

For multi-module changes produce `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` and `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`; include exact file ownership and state/interface contracts. Never restructure the app just to match the design without a test-backed reason.

## Implementation phases

1. Red: failing regression or demonstrable reproduction for each accepted criterion.
2. Green: smallest implementation in the actual runtime path; preserve security invariants.
3. Refactor: bounded simplification with full test rerun and source-level review.
4. Device: test Android permission, VPN and UI flows on disposable emulator when relevant.
5. Docs: update affected `docs/features`, `docs/modules`, `FEATURE_MATRIX`, `BEHAVIOR_CONTRACTS`, privacy/security and `CHANGELOG` in the **same branch**.
6. Verify: Gradle/Python suites, `docs_sync --write` then `--check`, `docs_contract.py`, diff review.
7. Release handoff: summarize migrations, risk, tests, unresolved items and monitoring without fabricating claims.

## Definition of done

Acceptance cases implemented and verified, no broken protected domains, no privacy regression or new attack surface without mitigation, performance measured if claimed, clear recovery states, docs consistent with code, release semantics unchanged unless requested.
