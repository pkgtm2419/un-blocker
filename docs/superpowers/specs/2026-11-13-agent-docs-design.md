# Antigravity documentation architecture — design specification (2026-11-13)

## Intent

Provide source-grounded context to an Antigravity agent working on `un-blocker` without hallucinating features, regressing DNS/privacy guarantees or letting docs go stale after new modules/features arrive.

## Chosen design

Layered Markdown: short universal `AGENTS.md` and scoped `.agents/rules/`, task-specific `.agents/skills/`, human-reviewed docs under `docs/`, and deterministic source inventories under `docs/generated/`. The reference download manager's docs are structure inspiration only. CI checks inventory drift; scheduled/push automation can open review PRs; humans/agents must maintain semantic docs in feature PRs.

## Alternatives considered

- One enormous agent prompt: easy to create, hard to discover/update, high context use — rejected.
- Fully AI-rewritten docs after every commit: risks making up functionality/privacy guarantees — rejected.
- Hybrid deterministic inventory plus source-reviewed docs: best reproducibility and correctness — selected.

## Constraints

No edits to original app code/signing/README/PRIVACY in the overlay, no account/backend invention, no claim of verified Android testing, no direct bot push to main. Support JDK 17/Gradle existing tooling and stdlib-only Python doc checks.

## Acceptance

Docs reference actual current paths/semantics; frontmatter valid; scripts deterministic/idempotent and tested offline; ZIP extracts relative to repository root; Antigravity can locate rule/skill entrypoints; PR workflow detects generated docs drift; source discrepancies remain visible until code-grounded correction.
