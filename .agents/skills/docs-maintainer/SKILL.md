---
name: docs-maintainer
description: Use when implementation, module structure, configuration, release behavior, privacy rules or user-visible features change and documentation may become stale.
---

# Docs Maintainer

## Purpose
Maintain two lanes: semantic docs through code-grounded review and deterministic inventories through a script; never auto-synthesize unsupported product/security claims.

## Workflow
1. Confirm branch/ref, inspect `git diff` plus affected production code and tests. Find relevant `docs/features`, `docs/modules`, `FEATURE_MATRIX.md`, `BEHAVIOR_CONTRACTS.md`.
2. Record **before vs after** behavior, users, limitations, data collected, permissions, migrations and test evidence. Update human-owned files in the same PR; edit existing README/PRIVACY only after verifying facts, not based on stale prose.
3. Run `python3 scripts/docs_sync.py --write`, review deterministic `docs/generated/` diff; update machine-produced inventory only through script.
4. Run `python3 scripts/docs_contract.py`, `python3 scripts/docs_sync.py --check`, `python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v`.
5. If claims conflict, log in `docs/REPO_RECONCILIATION.md` with file evidence. No invented success, benchmarks or release statuses.

## Quick example
A new `:app` settings toggle requires a `docs/features/SETTINGS_AND_BOOT.md` change, feature matrix row, configuration/default tests, changelog entry and regenerated `BUILD_FACTS.md` only if build config changed.

## Common mistakes
Treating generated class names as product features; rewriting privacy policy without reviewing runtime storage; marking incomplete `.gsd` as delivered; relying on bot commits to keep semantic docs accurate.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
