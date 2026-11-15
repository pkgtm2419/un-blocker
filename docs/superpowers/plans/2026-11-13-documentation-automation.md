# Documentation and Antigravity automation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Install trustworthy documentation and repeatable source-driven drift checks for `un-blocker`.

**Architecture:** Root agent rules and scoped skills guide each change; human-owned docs capture product meaning and source-grounded exceptions; deterministic script generates code inventories, CI checks/opens PRs without direct production writes.

**Tech Stack:** Android Kotlin/Gradle + Python 3 stdlib + GitHub Actions + Antigravity `.agents/`.

**Spec:** `docs/superpowers/specs/2026-11-13-agent-docs-design.md`

## Global constraints

- Preserve existing app code, Gradle configurations, README, PRIVACY, `.gsd` and release CI unless separately reviewed.
- Do not auto-generate semantic product truths from symbols or use unreviewed external services.
- Never commit secrets, DNS histories, or fabricated test outcomes.

## Review focus

- Fresh ZIP extracted into source checkout with dot directories included.
- Existing generated files drift after a Kotlin module/manifest/config edit.
- Absent first-install generated files bootstrap with explicit `--write`.
- Rule/skill Markdown malformed frontmatter fails a contract check.
- GitHub Actions cannot open PR due repository token permissions: fail visibly, not silently claim success.

### Task 1: Install agent guidance

**Files:** `AGENTS.md`, `PRODUCT.md`, `SECURITY.md`, `DESIGN.md`, `.agents/rules/*.md`, `.agents/skills/*/SKILL.md`.

- [ ] Validate paths against runtime Kotlin code and manifest.
- [ ] Check skills frontmatter and no contradictory unverified claims.
- [ ] Commit instructions on a feature branch.

### Task 2: Install architecture and feature documentation

**Files:** `docs/*.md`, `docs/modules/*.md`, `docs/features/*.md`.

- [ ] Check feature-to-source and module ownership maps.
- [ ] Reconcile contradictions with code/test evidence, keep investigation queue.
- [ ] Commit docs with the owning code change.

### Task 3: Generate deterministic inventories

**Files:** `scripts/docs_sync.py`, `docs/generated/*.md`, `scripts/tests/test_docs_tooling.py`.

- [ ] Write/run a temp-fixture failing drift regression test.
- [ ] Run `python3 scripts/docs_sync.py --write` in full repo checkout.
- [ ] Run `python3 scripts/docs_sync.py --check` and tests, expect zero drift.

### Task 4: Enforce reviewable CI automation

**Files:** `.github/workflows/documentation-{check,sync}.yml`, `scripts/docs_contract.py`.

- [ ] Run docs contract and unit tests.
- [ ] Verify PR check rejects an altered generated inventory.
- [ ] Verify scheduled/main trigger creates a proposed PR with permission, without direct main write.

### Task 5: Verify operational uptake

- [ ] Open Antigravity at repo root; inspect discovered skills/rules.
- [ ] Simulate one source/API/feature change, verify agent updates semantic docs and generated docs.
- [ ] Record check results, review PR and close source reconciliation items only on evidence.
