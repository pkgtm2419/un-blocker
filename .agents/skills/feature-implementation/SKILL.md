---
name: feature-implementation
description: Use when adding a new Un-Blocker feature or changing user-visible behavior across Kotlin modules or settings.
---

# Feature Implementation

## Workflow
1. Read `AGENTS.md`, `PRODUCT.md`, `docs/FEATURE_MATRIX.md`, the relevant module/feature guide. Trace actual caller from `MainActivity` or manifest service; verify existing tests.
2. Specify user story, exact input/output examples, error/denied/offline states, privacy and Android version risks in `docs/FEATURE_CHANGE_TEMPLATE.md` format. For multi-step work use a dated `docs/superpowers/specs` and `plans` file.
3. Write a failing regression test; show original failure. Implement minimal, module-owned behavior. Preserve `DecideBlockingUseCase` precedence and no network/telemetry surprises.
4. Run targeted unit tests then `./gradlew testDebugUnitTest lintDebug assembleDebug`, Python tooling and emulator if platform paths changed. Record NOT RUN for unavailable checks.
5. Update feature and module docs, feature matrix, behavior contracts, product/privacy/README as warranted, changelog. Run `/docs-maintainer` and security review.
6. Review diff for secrets, destructive migrations, hidden resolver changes, CI weakening; PR with test evidence and rollback.

## Example
A new tier exception requires a `DecideBlockingUseCaseTest` table case, tier-specific compatibility rationale, no protected-domain regression and `docs/features/FILTERING.md` update.

## Mistake to avoid
Adding UI controls without wiring settings/service and on-device regression.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
