# Documentation lifecycle, drift detection and safe automation

## Two distinct update lanes

**Lane A — machine-generated inventory:** `scripts/docs_sync.py` reads checked-out Gradle files, Kotlin source declarations/test names and extension JSON to generate `docs/generated/*.md`. Running `--check` compares exact UTF-8 bytes with tracked docs and fails on stale inventory. `--write` owns only this generated folder. Generated content has a machine header and stable ordering; never hand-edit it.

**Lane B — semantics and user-facing documentation:** human/Antigravity agent reviews each relevant code diff against features, threats and tests. Update `PRODUCT.md`, `SECURITY.md`, `PRIVACY.md` (existing root original when actually maintaining it), `docs/features/`, module notes, behavior contracts and `CHANGELOG.md` **in the same PR**. Scripts cannot safely infer promises, security claims or feature completeness from symbol names.

## Automation triggers

- PR to main: `.github/workflows/documentation-check.yml` checks contracts and drift against changed source.
- Push to main / scheduled run / manual dispatch: `.github/workflows/documentation-sync.yml` writes generated Markdown and uses `gh pr create` to propose changes; never directly pushes to main.
- Local Antigravity feature task: `AGENTS.md` and `/docs-maintainer` require update before completion. If bot PR is blocked by repository permissions, manually run `--write` and open a human-reviewed PR.

**Bootstrap:** Run `docs_sync.py --write` and commit all five inventories. `--check` fails if any or all outputs are missing, or if contents drift; deleting the generated folder cannot bypass CI.

**Contract scope:** root Markdown and maintained Markdown under `docs/`, `.agents/`, `.github/` and `.gsd/`. Imported originals under `unb/`, Git metadata and Android build outputs are outside this scope. Relevant saved documents are indexed under `docs/references/`; copied historical records carry provenance notices. Code-fenced Markdown examples are not interpreted as real links. Missing local files in maintained prose fail validation, including root README/privacy links.

**Interpreter:** Python 3.11 or newer, standard library only. On Windows, use a working Python executable if `python` resolves to a Microsoft Store alias; `py -3` is another option where the launcher is installed.

## How to add a feature

1. Create/modify implementation + tests; capture exact diff and feature ID.
2. Add `docs/features/<topic>.md` and a `FEATURE_MATRIX` row; update ownership/behavior/security/privacy docs.
3. If Gradle modules, extension manifest or source declarations changed, run `python3 scripts/docs_sync.py --write` from root and review generated diff.
4. Run `python3 scripts/docs_contract.py` then `python3 scripts/docs_sync.py --check`, and stage in same commit/PR.
5. Review a real-device/CI test record; avoid auto-publishing unverified docs.

## When checks fail

- Drift: run `--write` in updated source checkout, review source and commit inventory.
- Broken source link: correct the path, or mark a page as intentionally historical without linking to nonexistent content.
- Frontmatter invalid: repair `.agents/rules/*.md` trigger or `.agents/skills/*/SKILL.md` required fields.
- Missing business meaning: agent must inspect code/acceptance cases; a script cannot fix this correctly.

Read [Agent workflow](AGENT_WORKFLOW.md) and [generated inventory](generated/README.md).
