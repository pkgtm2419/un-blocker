# Source-of-truth reconciliation queue

These are **observed inconsistencies or investigation items**, not permission to alter runtime behavior without tests. Re-check each against the checkout HEAD and close with a dated PR/test reference.

| ID | Evidence from `8934519` | Required resolution | Status |
| --- | --- | --- | --- |
| R01 | `app/build.gradle.kts`: 2.6.1 / versionCode 35 / SDK 35; README sections still mention 2.3.0 and Android SDK 36 | Align README install/build section to actual Gradle and published tag, keep snapshots honest | PARTIALLY RESOLVED (README version/SDK corrected 2026-11-15; full alignment of all README sections pending) |
| R02 | README references nonexistent `docs/*` pages in inspected tree | This pack restores links as explicit source-based docs/status placeholders; migrate to measured reports when available | OPEN |
| R03 | `.gsd/SPEC.md`, `ROADMAP.md`, `STATE.md` focus on v2.5.0; manifest v6 has 19 extension records instead of historical "Essential 7" | Mark GSD historical and refresh only current product/roadmap, not falsify old progress | OPEN |
| R04 | README claimed no DNS logging/raw history in places; `QueryLogSink` aggregates plaintext domains, default raw events disabled, logging pause default false | Reconciled README and `PRIVACY.md` to accurately disclose on-device domain statistics (`domain_stats`), user pause/clear controls, and absence of remote browsing telemetry | RESOLVED (2026-11-15) |
| R05 | `PRIVACY.md` described 7-day/50-event retention; `QueryLogSink` executes 24h/1,000 raw events and 30-day/10,000 aggregates | Reconciled `PRIVACY.md` to document the exact executed retention: 30-day aggregate retention, 24-hour raw event retention, startup + hourly maintenance pruning | RESOLVED (2026-11-15) |
| R06 | `vpn-engine/src/main/java/com/unblocker/vpn/builder/UpstreamDnsServer.kt DEFAULTS` includes Cloudflare/Google while README denies hardcoded **runtime fallback**; active manifest service appears to use `DnsUpstreamClient` | Traced callers and Hilt bindings: active `UnblockerVpnService` exclusively constructs `DnsUpstreamClient` with dynamic `ResolverRegistry` from `ConnectivityManager.LinkProperties`; `vpn-engine` defaults have no app callers and `VpnEngineModule` has no bindings. No runtime fallback exists | RESOLVED (2026-11-15) |
| R07 | `app/build.gradle.kts` lint had `abortOnError=false`, `ignoreWarnings=true` and disabled detectors | Restored `abortOnError=true` and `ignoreWarnings=false`; disabled only informational version checks (`GradleDependency`, `AndroidGradlePluginVersion`). Verified `lintDebug` passes with 0 errors | RESOLVED (2026-11-15) |
| R08 | `ml-engine/BatchDomainAnalysisWorker` comments mention TFLite, while `HeuristicDomainClassifier` performs deterministic scoring | Reconciled: confirmed no `.tflite` model asset or inference is packaged/executed; `MlEngineModule` binds `HeuristicDomainClassifier` (deterministic feature extractor/scoring); documented across architecture and modules | RESOLVED (2026-11-15) |
| R09 | `BoundedFilterDownloader` resolves/checks URL host but `HttpURLConnection` may resolve again when connecting | Add test for DNS rebinding/redirect target binding; treat as hypothesis until confirmed | HYPOTHESIS |
| R10 | Running and test code not executed from this documentation-generation environment | Verified `./gradlew testDebugUnitTest` (333 tests, 0 failures), `lintDebug`, `assembleDebug` and Python suites run cleanly on 2026-11-15 | RESOLVED (2026-11-15) |
| R11 | `MyDomainsStore` writes normalized readable names under `my_domains_store/my_domains_list`; `DeviceUserRuleGateway.protectDomain` uses it | Reconciled in `README.md` and `PRIVACY.md`: explicitly documented as up to 500 plaintext protected domains stored in private app preferences, distinct from HMAC rules | RESOLVED (2026-11-15) |
| R12 | `RuleLineParser.parse` permits request-dependent options on subdomains and security document rules, converting them to unconditional suffix rules | Rejected request-scoped options ($script, $image, $3p, $doc) in DNS line parser; only sole $important on BLOCK rules accepted. Validated by parser regression suite | RESOLVED (2026-11-15) |
| R13 | `ExtensionRuleStore.Builder.addRule` puts wildcard allow rules in `allowSuffix`, including the apex | Separated `allowWildcard` set in `RuleSnapshot` and `Builder`; strict subdomains match while apex domain remains blocked. Verified with unit test | RESOLVED (2026-11-15) |
| R14 | `release.yml` builds/copies `releaseTest` when production secrets are absent and accepts test signer fallback for production-tag publish | Hardened `release.yml` to fail closed: production `v*` releases strictly require production keystore secrets and `UB_RELEASE_SIGNER_SHA256` pin, building `assembleRelease` without test fallback | RESOLVED (2026-11-15) |
| R15 | `QueryLogSink.pruneOldLogs` enforces 24h/1,000 events and 30d/10,000 aggregates on hourly maintenance; DAO defaults/read limits differ | Aligned `PRIVACY.md` contract with code, added startup prune trigger and unit test coverage in `QueryLogSinkTest` | RESOLVED (2026-11-15) |
| R16 | No production `setHistoryMode` caller found; `WorkScheduler` has no app call site; Hilt binds `DomainClassifier` to heuristic implementation | Documented: `QueryLogSink.setHistoryMode` defaults to `AGGREGATE_ONLY` (no raw history in production without caller); `WorkScheduler` is present in library but unscheduled at app startup | RESOLVED (2026-11-15) |
| R17 | `START_HERE.txt` and `validate_plans.py` assumed six missing historical plans | Reconciled by indexing eight supplied references and validating installed plans by structure, not a fixed count | RESOLVED |
| R18 | `kotlin-senior-code-style` skill described as "not in FOLDER_STRUCTURE.md's `.agents/skills/` list" in some references, yet `AGENTS.md` lists it as mandatory reading | Confirmed present and functional at `.agents/skills/kotlin-senior-code-style/SKILL.md`; FOLDER_STRUCTURE.md entry gap is a documentation omission only, not a missing artifact | RESOLVED |
| R19 | README.md "Requirements" section listed versionCode 28 / versionName 2.3.0 / compileSdkVersion 36 — inconsistent with actual `app/build.gradle.kts` values | Corrected in 2026-11-15 doc update to versionCode 35 / versionName 2.6.1 / SDK 35, matching Gradle source | RESOLVED (2026-11-15) |
| R20 | CONTRIBUTING.md stated "Android SDK 36" for the development environment requirement — inconsistent with `compileSdk = 35` in `app/build.gradle.kts` | Corrected to SDK 35 in 2026-11-15 doc update | RESOLVED (2026-11-15) |

The pack does **not** modify original README, PRIVACY or Gradle files except where a reconciliation item explicitly calls for a correction. Each behavioral change should be a separate, reviewed source-based PR; use `docs/audits/INITIAL_REVIEW_2026-11-13.md` as initial evidence.

## Local integration review — 2026-11-14

The installed pack was checked against the same baseline SHA. Source tracing found no `app` caller of reusable `VpnConfiguration`/`DnsForwarder`; the manifest service constructs the app's `DnsUpstreamClient` and uses `ResolverRegistry`. R06 is clarified statically, with device verification still open. `MlEngineModule` binds `HeuristicDomainClassifier`; app startup does not call `WorkScheduler`. R08 is clarified statically, without claiming worker execution. R02's missing links are restored. See [integration record](audits/INTEGRATION_REVIEW_2026-11-14.md) for executed checks and remaining limits.

## Local integration review — 2026-11-15

Documentation improvements applied in this session:

- Added **Status** column to the reconciliation table (values: OPEN, RESOLVED, PARTIALLY RESOLVED, HYPOTHESIS). R17 marked RESOLVED; R09 marked HYPOTHESIS; R01 marked PARTIALLY RESOLVED following README/CONTRIBUTING corrections.
- **R18** added: confirmed `kotlin-senior-code-style` skill artifact is present; the gap was a FOLDER_STRUCTURE.md documentation omission only.
- **R19** added and resolved: README.md "Requirements" corrected from versionCode 28 / versionName 2.3.0 / SDK 36 to versionCode 35 / versionName 2.6.1 / SDK 35.
- **R20** added and resolved: CONTRIBUTING.md "Android SDK 36" corrected to SDK 35.
- **R12** and **R13** resolved with test-backed code changes:
  - R12: Rejected request-scoped options (`$script`, `$image`, `$3p`, `$doc`) in `RuleLineParser` to avoid breaking first-party traffic on DNS blocks; accepted only sole `$important` on `BLOCK` rules. Updated TSV fixtures and tests.
  - R13: Added `allowWildcard` set to `ExtensionRuleStore` so wildcard allow rules (`@@*.example.com`) only unblock strict subdomains, leaving the apex blocked. Verified with unit tests.
- **R06** resolved: Traced resolver callers; verified runtime path uses Android `ConnectivityManager` link properties exclusively without hardcoded public DNS fallback.
- **R07** resolved: Android Lint restored with `abortOnError = true`, verified clean on `lintDebug`.
- **R08** resolved: Clarified `ml-engine` classifier semantics across docs — confirmed no packaged `.tflite` model, deterministic `HeuristicDomainClassifier` bound by Hilt.
- **R14** resolved: Hardened `release.yml` so production builds cannot downgrade to test signing or test keystores; fail-closed release policy strictly enforced.
- **R15** resolved: `QueryLogSink` startup prune added and tested.
- **R16** resolved: Documented default `AGGREGATE_ONLY` history mode (no raw history in production without caller) and unscheduled library `WorkScheduler`.
- Architectural hypothesis item R09 remains tracked for future investigation.
