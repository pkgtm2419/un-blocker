# Improvement progress (source-grounded index)

The original README linked to this file but it was absent in the inspected `main` snapshot. Existing `.gsd/{SPEC,ROADMAP,STATE}.md` discusses a historically completed v2.5.0 milestone; it does not prove the currently checked-out 2.6.1 APK passed an independent acceptance run.

## Current evidence

- Current Gradle source: `app/build.gradle.kts` `versionName=2.6.1`, `versionCode=35`.
- Existing unit/instrumentation and GitHub Actions workflows are present; specific results have **not** been executed or inspected during this documentation build.
- Several data and version claims need reconciliation; see [reconciliation](REPO_RECONCILIATION.md) and [initial static audit](audits/INITIAL_REVIEW_2026-11-13.md).

## Track future milestones

Create `docs/superpowers/specs/YYYY-MM-DD-topic-design.md` and `docs/superpowers/plans/YYYY-MM-DD-topic.md`; track acceptance, tests, PR, build and release evidence before marking a phase completed. Do not copy stale `.gsd` completed flags as new successes.
