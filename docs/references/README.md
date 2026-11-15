# Saved project references

The files below were supplied in `unb/` and reviewed during documentation integration on 2026-11-14. Originals remain unchanged. These copies organize useful project context without treating proposed changes as implemented features. Missing original plan links are recorded as unavailable references, rather than fabricated plans.

The current implementation baseline is commit `8934519b021d56f12e90d824c20b4f60be1bda4b`. Use [behavior contracts](../BEHAVIOR_CONTRACTS.md), [source reconciliation](../REPO_RECONCILIATION.md), and [integration review](../audits/INTEGRATION_REVIEW_2026-11-14.md) for current conclusions.

| Imported document | Original SHA-256 | Status |
| --- | --- | --- |
| [2026-11-02-unblocker-improvement-audit.md](2026-11-02-unblocker-improvement-audit.md) | `a603d0b60c5bcae0e835cf879f2a912553fd6b414c8e87aae31f1ef29c4d97d1` | Historical/proposed; revalidate against current code |
| [2026-11-02-unblocker-lightweight-reliability-design.md](2026-11-02-unblocker-lightweight-reliability-design.md) | `5aa9b5e9a8a614c3b4dcbabc35dca3c1c3518295a1307d2b537ec0aaa3eb7972` | Historical/proposed; revalidate against current code |
| [deep-research-report (1).md](deep-research-report%20%281%29.md) | `2f8061ecda144fe0d70cd6b174496221fd4f35233673c1d496faf1c71e4ac30b` | Historical/proposed; revalidate against current code |
| [deep-research-report.md](deep-research-report.md) | `35d35b83ac92186f780dc22fd85c04b525041cfda85529a8dc3cab4cdb9e1d00` | Historical/proposed; revalidate against current code |
| [UN_BLOCKER_CONTRIBUTION_NOTES.md](UN_BLOCKER_CONTRIBUTION_NOTES.md) | `77fbc02ceffa1b14043561ef967aa41e6b78171a4788bfbcadbe2522ce6a4c47` | Historical/proposed; revalidate against current code |
| [UN_BLOCKER_QA_AUDIT_2026-11-02.md](UN_BLOCKER_QA_AUDIT_2026-11-02.md) | `810a0e92cee08b8659cdde99596a9fe415b85c6eeb0e168d88a9392fd5a4e8e8` | Historical/proposed; revalidate against current code |
| [UN_BLOCKER_TEST_EXECUTION_PLAN_2026-11-02.md](UN_BLOCKER_TEST_EXECUTION_PLAN_2026-11-02.md) | `79036fd4548b7048fa20694ff17d6abde7838c8b218c6e5c4ded6041b0caff59` | Historical/proposed; revalidate against current code |
| [UNBLOCKER_IMPROVEMENT_PLAN.md](UNBLOCKER_IMPROVEMENT_PLAN.md) | `fa2d34663a8bbb11591a0b537786f9510074048327d616af6ae7e5757af48ccd` | Historical/proposed; revalidate against current code |

## Other saved inputs

- `un-blocker-antigravity-documentation.zip`: 98 entries, ZIP CRC verified locally before installing. Its original plan/design remain under `docs/superpowers/` as provenance; current validation is in the integration review.
- `unb/UN_BLOCKER_QA_PACKAGE.zip`: contains the supplied historical QA audit, execution plan and `CI_JUNIT_SUMMARY.json`. The audit and plan are copied above. The JSON is preserved alongside these references; its historical results are not current test passes.
- `unb/un-blocker-contribution-starter.zip`: contains the same contribution notes and a source patch. The patch is **not applied**; implementing its behavior changes requires a separate task with regressions and Android validation.
- `unb/un-blocker-filter-safety-contribution.patch`: proposed changes for conditional-rule admission and wildcard allows; current source still has the reported behavior. This installation documents it.
- `unb/02-process-sequences.md`: unrelated CMMS customer manual. Excluded from Un-Blocker documentation; original left in place.
- `unb/chat.txt` and `unb/un-blocker.json`: conversation/research inputs, retained in their original location. They do not prove current runtime behavior and are not published as engineering contracts.

## Missing historical work

The saved 2026-11-02 design references six detailed implementation plans. Neither supplied archive contains those plans. `START_HERE.txt` and `validate_plans.py` previously assumed those files existed. They now point to and validate the installed documentation instead of requiring fictional file counts. Historical plans are not reconstructed as completed work.
