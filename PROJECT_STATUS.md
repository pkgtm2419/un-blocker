# Un-Blocker — Project Status

> Last updated: 2026-11-15  
> Source baseline: `main` @ `8934519` (2026-11-10)  
> Repository: <https://github.com/pkgtm2419/un-blocker>

---

## 1. Current Version & Build Status

| Property | Value |
|---|---|
| **versionName** | `2.6.1` |
| **versionCode** | `35` |
| **applicationId** | `com.unblocker.app` |
| **minSdk** | `26` (Android 8.0 Oreo) |
| **compileSdk / targetSdk** | `35` (Android 15) |
| **Android Gradle Plugin** | `8.10.1` |
| **Kotlin** | `2.0.21` |
| **JVM target** | `17` |
| **Latest published tag** | `v2.6.1` |

> [!NOTE]
> `app/build.gradle.kts` is the authoritative source for all build properties. The root `README.md` previously contained stale version references (`2.3.0`, SDK 36); those are being corrected separately (reconciliation item R01).

---

## 2. Milestone History

> [!NOTE]
> The `.gsd/ROADMAP.md` and companion files (`SPEC.md`, `STATE.md`) are **historical records** for the v2.5.0 milestone. They do not represent the full v2.6.1 changelog. Mark these as past evidence, not current claims.

### v2.5.0 — Essential Extensions, Tiered Filtering & UI Performance

| Phase | Description | Status |
|---|---|---|
| **Phase 1** | Engine Optimization & State Synchronization Fix | ✅ Done |
| **Phase 2** | Curate the Essential 7 Extension Pack & Parser Pruning | ✅ Done |
| **Phase 3** | Tiered Filtering (Basic / Optimal / Complete) & Mobile Unbreak | ✅ Done |
| **Phase 4** | UI Performance, Page Navigation & Compose Optimization | ✅ Done |
| **Phase 5** | Comprehensive Verification, APK Build & GitHub Release | ✅ Done |

**Phase 1 key tasks (done):**
- VPN toggle disconnect fix across `UnblockerScreen.kt`, `MainScreen.kt`, `QuickStartManager.kt`
- `OffHeapRuleEngine` wired to `AdDetector.kt` and `DecideBlockingUseCase.kt`
- Eliminated `substring` allocation in `ExtensionRuleStore.kt`
- Quarantine entry added for `deofleadyetca.org`

**Phase 2 key tasks (done):**
- `extensions_manifest.json` overhauled from 53 → Essential 7 mobile-optimized lists
- `BoundedFilterDownloader.kt` and `RuleLineParser.kt` updated to strip cosmetic-only rules

**Phase 3 key tasks (done):**
- `FilteringTier` enum (`BASIC`, `OPTIMAL`, `COMPLETE`) added to `FilteringPreferences.kt`
- `MobileUnbreakList.kt` protecting Supercell, Spotify, Play Store, WhatsApp CDN, banking domains
- Tier logic wired into `DecideBlockingUseCase.kt`

**Phase 4 key tasks (done):**
- 300 ms debounce on Room emissions in `LogViewModel.kt`; `take(150)` windowing in `LogViewerScreen.kt`
- Tier Selection Card added to `UnblockerScreen.kt`
- `ExtensionsScreen.kt` optimized for Essential 7

**Phase 5 key tasks (done):**
- All unit tests run (`./gradlew testDebugUnitTest`)
- Release test APK built (`./gradlew assembleReleaseTest`)
- `python scripts/release-policy.py preflight` passed
- Version bumped to `2.5.0` (versionCode 33), tagged `v2.5.0`, pushed to GitHub

> [!NOTE]
> v2.6.1 (versionCode 35) is the current checkout. The two version increments from 2.5.0 → 2.6.1 are not documented in the GSD roadmap. No `.gsd` milestone document covers v2.6.x; track future work in `docs/superpowers/`.

---

## 3. CI / Test Coverage

| Workflow | Trigger | Scope | Lint Gate |
|---|---|---|---|
| `android.yml` | push / PR | Hygiene, script/compile tests, unit tests, lint, APK smoke | ✅ Enforced — `abortOnError=true` (resolved 2026-11-15) |
| `android-device-matrix.yml` | Nightly + manual | Instrumentation on API 26 / 30 / 35 | — |
| `codeql.yml` | Schedule + push/PR | Static analysis (CodeQL) | ✅ |
| `release.yml` | Tag / manual | Signed release build with preflight | ✅ Enforced — fail-closed without test-signer fallback (R14 resolved) |
| `documentation-check.yml` | push / PR | Docs sync & contract checks | ✅ |
| `documentation-sync.yml` | push | Docs inventory generation | ✅ |

### Test commands

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Android lint (soft gate — see R07)
./gradlew lintDebug

# Debug APK smoke build
./gradlew assembleDebug

# Instrumentation (requires emulator/device with VPN permission)
./gradlew connectedDebugAndroidTest

# Rule compiler Python tests
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v

# Script/docs tooling tests
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/tests/run-all.sh

# Documentation pack checks
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
```

> [!NOTE]
> CI lint is now enforced as a hard gate (`abortOnError=true`, `ignoreWarnings=false`). See R07.

---

## 4. Known Issues & Reconciliation Items

> [!NOTE]
> These are **observed inconsistencies from static code review** at `8934519`. Each requires a separate, reviewed, source-based PR before the item is closed. Do not alter runtime behavior as a side effect of documentation fixes.

| ID | Severity | Description | Status |
|---|---|---|---|
| **R01** | ✅ Done | README had stale version (`2.3.0`) and SDK level (`36`); corrected to 2.6.1 / SDK 35 on 2026-11-15 | ✅ Done |
| **R02** | ⚠️ Issue | README referenced nonexistent `docs/*` pages; now restored as source-based placeholders | ✅ Done |
| **R03** | ⚠️ Issue | GSD files focus on v2.5.0; current manifest has 19 extension records vs historical "Essential 7" | 📋 Planned |
| **R04** | ✅ Done | README and `PRIVACY.md` reconciled to accurately disclose bounded on-device domain statistics | ✅ Done |
| **R05** | ✅ Done | `PRIVACY.md` reconciled to document exact retention: 30-day aggregates, 24-hour raw events | ✅ Done |
| **R06** | ✅ Done | `vpn-engine` `UpstreamDnsServer.DEFAULTS` audited; verified `app` uses `DnsUpstreamClient` with `ConnectivityManager` link properties exclusively (no fallback to public DNS) | ✅ Done |
| **R07** | ✅ Done | `abortOnError=true` and `ignoreWarnings=false` restored; verified `lintDebug` passes with 0 errors | ✅ Done |
| **R08** | ✅ Done | Confirmed no `.tflite` model packaged or loaded; `MlEngineModule` binds `HeuristicDomainClassifier` (deterministic scoring); documented across docs | ✅ Done |
| **R09** | 📋 Planned | `BoundedFilterDownloader` DNS rebinding / redirect target binding — hypothesis, not confirmed | 📋 Planned |
| **R10** | ✅ Done | Full Gradle unit test suite (335 tests, 0 failures), lint, and APK build verified on 2026-11-15 | ✅ Done |
| **R11** | ✅ Done | `MyDomainsStore` / `protectDomain` now documented separately from HMAC rules in README and PRIVACY | ✅ Done |
| **R12** | ✅ Done | `RuleLineParser.parse` rejects request-scoped options (`$script`, `$image`, `$3p`, `$doc`); only sole `$important` on `BLOCK` accepted — verified by unit tests | ✅ Done |
| **R13** | ✅ Done | `ExtensionRuleStore` separates `allowWildcard` set from `allowSuffix`; apex remains blocked while subdomains unblock — verified by unit tests | ✅ Done |
| **R14** | ✅ Done | `release.yml` hardened to fail closed without test-keystore fallback; production `v*` tags strictly require release secrets and pinned signer | ✅ Done |
| **R15** | ✅ Done | `PRIVACY.md` aligned with `QueryLogSink.pruneOldLogs`; startup prune added; unit test verified | ✅ Done |
| **R16** | ✅ Done | Documented API availability: `QueryLogSink.historyMode` defaults to `AGGREGATE_ONLY` (no raw history in production without caller); `WorkScheduler` is present in library but unscheduled at app startup | ✅ Done |
| **R17** | ✅ Done | `START_HERE.txt` / `validate_plans.py` assumed 6 missing plans — reconciled by indexing 8 references | ✅ Done |

### Privacy & data-retention detail (R04 / R05 / R15)

> [!NOTE]
> `PRIVACY.md` and `README.md` have been updated on 2026-11-15 to match implemented behavior in `QueryLogSink`: **30-day / 10 000 aggregate retention** and **24 h / 1 000 event retention**, pruned at service startup and hourly. The "My domains" protection list (up to 500 domains) is also explicitly documented as readable names in private app preferences.

---

## 5. Open Work Items

| Item | Source | Status |
|---|---|---|
| Reconcile `PRIVACY.md` retention wording with `QueryLogSink` implementation (R05 / R15) | REPO_RECONCILIATION | ✅ Done |
| Enforce lint as hard gate (`abortOnError=true`) incrementally (R07) | REPO_RECONCILIATION | ✅ Done |
| Confirm or remove vpn-engine Cloudflare/Google defaults from active resolver path (R06) | REPO_RECONCILIATION | 📋 Planned |
| Correct ml-engine TFLite references / worker scheduling docs (R08 / R16) | REPO_RECONCILIATION | 📋 Planned |
| Fix `release.yml` test-certificate fallback risk (R14) | REPO_RECONCILIATION | ⚠️ Issue |
| Conduct 24-hour physical device soak test | improvement-progress.md | 📋 Planned |
| Independently verify production signing end-to-end | improvement-progress.md | 📋 Planned |
| Obtain independently certified false-positive rate measurement | improvement-progress.md | 📋 Planned |
| Document v2.6.x changes in a proper milestone spec/plan under `docs/superpowers/` | improvement-progress.md | 📋 Planned |
| Validate `RuleLineParser` admission semantics (R12) | REPO_RECONCILIATION | 📋 Planned |
| Validate wildcard allow-rule storage in `ExtensionRuleStore` (R13) | REPO_RECONCILIATION | 📋 Planned |

---

## 6. Roadmap / Next Steps

> [!NOTE]
> Items below marked **ASSUMPTION:** are inferred from project patterns and are not explicitly stated in any checked-in document. All other items are grounded in source files or reconciliation records.

| Priority | Item | Basis |
|---|---|---|
| 🔴 High | Fix privacy policy retention mismatch (R15) before next public release | REPO_RECONCILIATION R05/R15 |
| 🔴 High | Enforce release signing — eliminate test-cert fallback (R14) | REPO_RECONCILIATION R14 |
| 🟠 Medium | Restore lint as a hard CI gate (R07) | REPO_RECONCILIATION R07 |
| 🟠 Medium | 24-hour physical device soak test for VPN stability | improvement-progress.md |
| 🟠 Medium | Confirm or isolate vpn-engine hardcoded DNS defaults (R06) | REPO_RECONCILIATION R06 |
| 🟡 Low | Certify false-positive rates with an independent dataset | improvement-progress.md |
| 🟡 Low | Correct ml-engine docs to reflect heuristic classifier (R08) | REPO_RECONCILIATION R08 |
| 🟡 Low | ASSUMPTION: Plan v2.7.x milestone in `docs/superpowers/specs/` with explicit acceptance criteria | Pattern from v2.5.0 workflow |
| 🟡 Low | ASSUMPTION: Evaluate restoring more extension lists beyond Essential 7 as opt-in (R03) | R03 observation |
| 📋 Backlog | Add DNS rebinding / redirect target binding test for `BoundedFilterDownloader` (R09) | REPO_RECONCILIATION R09 |
| 📋 Backlog | Review `RuleLineParser` subdomain option conversion semantics (R12) | REPO_RECONCILIATION R12 |

---

## Appendix: Module Map

| Gradle Module | Purpose |
|---|---|
| `:app` | Main application — VPN service, UI (Compose), use cases, DNS logic |
| `:common` | Shared utilities and models |
| `:data-store` | Room database, DAOs, data persistence |
| `:vpn-engine` | Low-level VPN/DNS packet handling and upstream resolver |
| `:ml-engine` | Domain classification (currently `HeuristicDomainClassifier`) |

---

*This file is maintained by the development team. For build/test evidence, fill in the verification record template in [docs/BUILD_AND_TEST.md](docs/BUILD_AND_TEST.md). For reconciliation fixes, open a source-based PR and update [docs/REPO_RECONCILIATION.md](docs/REPO_RECONCILIATION.md).*
