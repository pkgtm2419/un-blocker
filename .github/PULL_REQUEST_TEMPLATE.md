# Change summary

Describe the user-visible requirement, old behavior, new behavior and exact affected modules/screens.

## Requirement and acceptance

- [ ] Input/output acceptance criteria and error/denied/offline cases are covered.
- [ ] Actual production caller path identified (do not assume unused `vpn-engine` classes are active).
- [ ] No unapproved change to rule precedence, Android DNS routing or privacy boundaries.

## Testing evidence

| Command and environment | PASS/FAIL/NOT RUN | Notes/CI URL |
| --- | --- | --- |
| `./gradlew testDebugUnitTest` | | |
| `./gradlew lintDebug assembleDebug` | | |
| `./gradlew connectedDebugAndroidTest` | | |
| `python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v` | | |
| `python3 scripts/docs_contract.py && python3 scripts/docs_sync.py --check` | | |

## Privacy, security, performance and migration

Explain changed DNS boundaries, data storage/export/logging, remote downloads, signing/permissions, migrations, performance claims, and mitigation tests. Do not attach real personal DNS names or credentials.

## Documentation updates

- [ ] Relevant `docs/features/` and `docs/modules/` updated.
- [ ] `docs/FEATURE_MATRIX.md`, `docs/BEHAVIOR_CONTRACTS.md`, and `CHANGELOG.md` updated when relevant.
- [ ] README/PRIVACY/SECURITY/datasets reviewed if claims changed.
- [ ] Ran `python3 scripts/docs_sync.py --write`, reviewed and committed `docs/generated/` output in full checkout.
- [ ] Known contradictions recorded or resolved in `docs/REPO_RECONCILIATION.md`.

## Rollback and release notes

Specify safe rollback, compatible stored data, devices/APIs tested, and remaining verification gaps. Do not assert that skipped checks passed.
