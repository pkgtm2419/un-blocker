# Documentation integration review — 2026-11-14

Baseline: `8934519b021d56f12e90d824c20b4f60be1bda4b`; local branch `codex/install-antigravity-docs`. Scope: install and reconcile the supplied documentation package. This is not an application security audit or Android acceptance run.

## Installed material

- All 98 ZIP entries installed with CRC and path-containment checks; existing guides matched the archive before extraction.
- Ten task skills, eight scoped rules, two documentation workflows, root contracts, module/feature guides and five generated inventories.
- Eight project-related Markdown inputs copied to `docs/references/` with provenance notices and original SHA-256 digests. Originals, archives and proposed patch preserved; unrelated CMMS material excluded.
- Missing six historical plans recorded as unavailable. Legacy start guide and validator now describe the installed documentation.

## Current source conclusions

| Area | Observed source | Documentation consequence |
| --- | --- | --- |
| Build | 2.6.1, code 35; min SDK 26; compile/target 35; JVM target 17 | Source facts supersede stale prose |
| Policy | User allow before block; unmatched APPROVED_ONLY allows; errors fail open | Exact outcomes recorded |
| Privacy | HMAC rule/evidence stores; readable My domains preferences and Room aggregates | Separate storage disclosures |
| Retention | Hourly pruning targets 24h/1,000 events and 30d/10,000 aggregates | Periodic targets, not immediate hard bounds |
| Raw history | Aggregate-only default; no production enabling caller found | API distinguished from UI opt-in |
| Resolvers | Manifest service uses DnsUpstreamClient/ResolverRegistry; no app caller of reusable DnsForwarder/VpnConfiguration found | Reusable defaults do not prove active fallback |
| Batch ML | Hilt binds HeuristicDomainClassifier; no app WorkScheduler call found | No deployed model or guaranteed nightly learning claimed |
| Rule import | Conditional options become suffix rules; wildcard allows share suffix storage | Gaps documented; patch unapplied |
| Release | Existing workflow can use releaseTest/test signer fallback for production tags | Intended policy differs from enforcement |

## Verification

Python 3.15.0 used locally. Tooling uses only the standard library; Python 3.11 grammar compatibility is checked separately. Added regression cases were run before fixes and exposed four failed assertions and a malformed-entry exception. A Windows short-path alias exposed the need to resolve the contract root consistently.

```powershell
python scripts/docs_sync.py --write
python scripts/docs_contract.py
python scripts/docs_sync.py --check
python -m unittest discover -s scripts/tests -p "test_docs_*.py" -v
python validate_plans.py
```

Use a working interpreter path if Windows `python` resolves to the Microsoft Store. Final execution results are recorded after verification. Contract scope covers maintained root Markdown and the `docs`, `.agents`, `.github`, and `.gsd` trees. Supplied `unb` inputs and Android build outputs are outside scope. All five inventories must exist and match source.

Executed locally with zero exit status:

| Check | Result |
| --- | --- |
| Archive installation | All 98 package paths present; ZIP CRC valid |
| Documentation contract | 130 maintained Markdown files, 10 skills, 8 rules validated |
| Inventory generation and drift | All five outputs present and match source |
| Documentation regression suite | 15 tests passed |
| Full `scripts/tests` Python discovery | 17 tests passed, including two existing validation tests |
| Plan validation | Installed structure and maintained links passed |
| Python 3.11 grammar | All four changed Python files parse; execution used Python 3.15 |

An independent read-only review identified an overbroad `UserRuleGateway` privacy description. It was corrected to distinguish HMAC rule updates from plaintext protected-site storage. Final checks were rerun after the correction.

## Limitations

No Android compilation, emulator/device run, live network test, security scan, release artifact verification, GitHub workflow execution, Antigravity UI discovery check, commit, push or PR was performed. Historical QA/research assertions are attributed records, not current successes. Application source, Gradle, original README/privacy policy, `.gsd`, and release workflows remain unchanged. See [reconciliation](../REPO_RECONCILIATION.md).
