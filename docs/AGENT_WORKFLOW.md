# Antigravity agent execution contract

## Context loading strategy

At start: root `AGENTS.md` and `PRODUCT.md`. For targeted changes load only the relevant `docs/features/` guide, `docs/modules/` guide and scoped `.agents/rules/`, then a matching `.agents/skills/<name>/SKILL.md`. This avoids bloating context with every document at once. Follow the actual source code and tests; documentation is a navigation map, not evidence of a passing build.

Workspace rule paths and `trigger`/`description`/`globs` metadata were checked using Context7 against the [official Antigravity rules documentation](https://antigravity.google/docs/rules?tab=ide) during integration on 2026-11-14. Valid metadata does not substitute for verifying discovery in the installed agent UI.

## Workflow

**Investigate → Plan → Tests → Minimal implementation → Review → Documentation → Verification → PR.**

1. Confirm repository/ref and uncommitted state. Trace actual runtime callers, not only class names or README features.
2. Write acceptance criteria and relevant threat model; for nontrivial tasks create a short plan under `docs/superpowers/plans/` with exact paths and per-step tests.
3. Add a failing test or reproduce before editing; preserve observable current behavior unless the user authorized change.
4. Implement incremental, module-responsible changes. Respect security, data compatibility, Android lifecycle and privacy policy.
5. Run test matrix; treat unknown results as NOT RUN. If environment has no Android SDK/emulator, test what can run and explicitly record gaps.
6. Update human-owned docs *with code review*: product/feature, state diagrams, security/privacy, configuration, release notes, and root README if necessary.
7. Regenerate generated inventory (`docs_sync --write`), validate (`docs_contract` + `docs_sync --check`), and show diff.
8. PR summary includes changed behavior, source evidence, covered regressions, security implications and remaining caveats. Never auto-merge or publish without project approval.

## Agent delegation

Independent subagents may audit privacy, DNS tests and docs in parallel, but no two agents should edit the same file concurrently. A reviewer must reconcile source findings and execute relevant verification, not rely only on agent status. See superpowers process docs if that plugin is installed; this repository does not require proprietary plugin functionality to build.

## Ambiguity handling

When code, docs, tests or generated inventory disagree, stop semantic rewriting; open/review an issue in `REPO_RECONCILIATION.md`, mark source-observed and add a test to decide the claim. Auto-generation never invents application behavior.
