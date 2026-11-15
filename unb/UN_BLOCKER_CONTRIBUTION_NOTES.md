# Un-Blocker contribution: DNS filter safety

Prepared: 2026-11-11
Upstream: https://github.com/pkgtm2419/un-blocker
Upstream base revision: `8934519b021d56f12e90d824c20b4f60be1bda4b` (`main`)
Suggested feature branch: `contrib/filter-safety-custom-rules-2026-11-11`

## Remote publishing status

The connected GitHub account was `presage-pawan`. The repository metadata reported push access, but `create_branch` on `pkgtm2419/un-blocker` failed with HTTP 403: `Resource not accessible by integration`. The connected GitHub App installations returned accounts `presage-pawan` and `Kpreetsid`, not `pkgtm2419`. No upstream branch, push, or pull request was created. A local staging branch with the proposed name was initialized only for generating a patch.

The repository owner should authorize the GitHub connector/app for this repository with Contents read/write and Pull requests read/write permissions, or the user can fork the repository into an authorized GitHub account and submit a cross-fork PR.

## Changes in `un-blocker-filter-safety-contribution.patch`

1. Reject Adblock network rules with request-dependent options such as `$image`, `$script`, `$third-party`, and `$doc`. A DNS-only filter cannot enforce those conditions; converting them into hostname-wide bans can break an app's primary functionality. Retain plain block rules and block rules with only `$important`.
2. Preserve the distinct semantics of wildcard allow rules (`@@*.example.test`): allow subdomains, but not the base hostname. Previously the in-memory extension store promoted wildcard allow rules to unrestricted suffix allow rules.
3. Update existing JUnit assertions and parser TSV cases to pin the corrected behavior.

Modified paths:
- `app/src/main/java/com/unblocker/app/logic/rules/RuleLineParser.kt`
- `app/src/main/java/com/unblocker/app/logic/rules/ExtensionRuleStore.kt`
- `app/src/test/java/com/unblocker/app/logic/rules/ExtensionRuleStoreTest.kt`
- `app/src/test/resources/rule-parser-cases.tsv`

**Trade-off:** Rejecting conditional Adblock rules may reduce the number of imported, accepted filter entries. It is intentionally conservative to reduce accidental breakage. This change alone is not proof of improved advertisement-blocking percentage.

## Verification executed

- Reproduced the unsafe context-rule conversion on the original source: regression runner failed before the fix, as expected.
- Compiled the actual modified `RuleLineParser.kt` and `ExtensionRuleStore.kt` with `kotlinc` 1.9 plus minimal test dependency stubs; 17 focused regression checks passed.
- Compiled and invoked 6 existing `ExtensionRuleStoreTest` methods with minimal JUnit/Android dependency stubs; 6 passed.
- `git diff --check`: passed.
- `git apply --check` and `git apply` on an isolated checkout based on the captured upstream files: passed for all 4 changed paths.

**Not verified:** A real Android Gradle build, Android instrumentation, full repository tests, Android 16/17 physical-device behavior, filtering performance, battery, and ad-coverage percentage. The execution environment lacks the Android SDK and direct public GitHub network access; the tested checkout contains only the impacted upstream files and test resource, not the entire app. Do not treat this patch as production-ready without running the full upstream CI and device tests.

## Apply the patch

Once authorized for a branch/fork:

```bash
git clone https://github.com/pkgtm2419/un-blocker.git
cd un-blocker
git switch -c contrib/filter-safety-custom-rules-2026-11-11 origin/main
git apply --check /path/to/un-blocker-filter-safety-contribution.patch
git apply /path/to/un-blocker-filter-safety-contribution.patch
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
python -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
git add app/src/main/java/com/unblocker/app/logic/rules/RuleLineParser.kt app/src/main/java/com/unblocker/app/logic/rules/ExtensionRuleStore.kt app/src/test/java/com/unblocker/app/logic/rules/ExtensionRuleStoreTest.kt app/src/test/resources/rule-parser-cases.tsv
git commit -m "fix: prevent unsafe DNS rule broadening and wildcard allow leakage"
git push -u origin contrib/filter-safety-custom-rules-2026-11-11
```

Only after CI and device tests are passing, create a pull request targeting `main`, describing the conservative matching change and acceptance-rate trade-off.

## Remaining contribution roadmap (not implemented)

- Add an offline local filter-file importer with Android Storage Access Framework, validation preview, undo/rollback, and explicit consent.
- Audit and correct user-domain plaintext persistence while retaining user-editable domain lists.
- Audit filter-list provenance, policy precedence, rule compiler parity, and controlled refreshes.
- Verify release signing and make lint quality checks enforceable without masking existing issues.
- Expand Android 16/17 emulator/device test coverage, OEM lifecycle tests, and data-driven ad-block/false-positive measurements.
- Build per-app exception workflows and transparent 'why blocked' diagnostics with privacy-preserving defaults.

These require separate, reviewed changes; a blanket 'all issues fixed' claim would not be justified.
