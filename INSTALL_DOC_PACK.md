# Install the Antigravity documentation pack

This ZIP is an **overlay**, not a replacement repository checkout. Its top-level paths are relative to the root of `pkgtm2419/un-blocker` containing `settings.gradle.kts`.

## Installed checkout

The pack is installed and reconciled locally as of 2026-11-14 on `codex/install-antigravity-docs`. Start at [docs/README.md](docs/README.md), [saved reference index](docs/references/README.md), and [integration verification](docs/audits/INTEGRATION_REVIEW_2026-11-14.md). Application source and existing release workflows are unchanged. The original archive remains a provenance artifact; extracting it again with overwrite would replace local corrections.

Tooling requires **Python 3.11+**, standard library only. If `python` opens the Microsoft Store on Windows, use a real interpreter path or `py -3` where installed. `--check` now fails for missing inventories as well as stale content; always run `--write` for initial setup.

## Installation

1. Clone or open `https://github.com/pkgtm2419/un-blocker` and check out a feature branch.
2. Extract `un-blocker-antigravity-documentation.zip` **into the repository root**, preserving `.agents/`, `.github/`, `docs/`, `scripts/`, and the root Markdown files. Turn on "show hidden files" if using an archive GUI.
3. Review any conflicts before overwriting. The pack intentionally does **not** contain `README.md`, `PRIVACY.md`, `CONTRIBUTING.md`, existing workflows or `.gsd/*`; keep and reconcile those files rather than replacing them wholesale.
4. From the root run: `python3 scripts/docs_sync.py --write`, `python3 scripts/docs_contract.py`, `python3 scripts/docs_sync.py --check`, `python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v`.
5. Commit docs and `.agents/` to your working branch; open a PR to `main`, review the security/privacy items in `docs/REPO_RECONCILIATION.md`, and review CI permissions before enabling bot-generated PRs.
6. Open the repository as an Antigravity workspace. Instructions in `AGENTS.md` and scoped `.agents/rules/` are discoverable; `.agents/skills/<name>/SKILL.md` can be invoked by task or slash command.

## PowerShell equivalent

```powershell
Expand-Archive -Path .\un-blocker-antigravity-documentation.zip -DestinationPath . -Force
python scripts/docs_sync.py --write
python scripts/docs_contract.py
python scripts/docs_sync.py --check
python -m unittest discover -s scripts/tests -p "test_*.py" -v
```

## Automation behavior

- **On code PR:** `documentation-check.yml` detects if checked-in inventories drift and runs contract checks; it cannot write to the contributor branch automatically.
- **On push to `main`, schedule or manual dispatch:** `documentation-sync.yml` regenerates deterministic inventory files and **proposes a PR** using GitHub CLI when `GITHUB_TOKEN` allows creating pull requests. It does not silently rewrite `PRODUCT.md`, feature claims, security docs, or source code.
- **For new functionality:** the developer/Antigravity agent is instructed by `AGENTS.md` and `/docs-maintainer` to update the relevant human-owned docs in the same PR; CI flags deterministic changes and cross-reference issues.
- Enable repository Actions permissions to allow creating PRs if the sync job cannot do so. Review PRs; do not make automation a direct-push bypass of branch protection.

## Review and provenance

Based on `pkgtm2419/un-blocker@8934519b021d56f12e90d824c20b4f60be1bda4b` and documentation patterns (not product functionality) from `anefzaoui/misterdownloadmanager@4328526e8cda0d042b55bd62c41665727b556e72`, inspected 2026-11-13. Revalidate against source before applying to later commits. No Android build, device test, vulnerability scan, or live GitHub write was performed during generation.
