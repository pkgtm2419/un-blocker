# Un-Blocker v2.7.2 → v3.0.0: Feature-Parity & UX Upgrade Plan

**Repository:** `pkgtm2419/un-blocker` · **Baseline:** `main` @ `1dfd0c5` (v2.7.2, code 38, 2026-11-20)
**Install at:** `docs/superpowers/plans/2026-11-28-feature-parity-and-ux.md` (repo convention, see `AGENTS.md` step 1)
**Origin:** side-by-side comparison with `pass-with-high-score/blockads-android` (v7.2.1, GPL-3.0). Facts about un-blocker below were **verified in the code at `1dfd0c5`**; items marked *(verify)* were not.
**Feature IDs:** new features take **F25 and up** in `docs/FEATURE_MATRIX.md` (F01-F24 exist).

> **Honesty note.** Nothing in this plan was compiled or run. Effort sizes are rough (S ≈ ≤1 day, M ≈ 2-4 days, L ≈ 1-2 weeks of agent work plus review) and are not benchmarks. Do not claim a test passed unless it was executed; write `NOT RUN` otherwise (`docs/QUALITY_GATES.md`).

---

## 0. How to run this plan (paste to Antigravity)

```text
You are working in pkgtm2419/un-blocker (Android, Kotlin, Compose, Hilt, Room, 5 Gradle modules).
Read, in this order: AGENTS.md, .agents/rules/*.md, docs/QUALITY_GATES.md, docs/FEATURE_MATRIX.md,
docs/FEATURE_CHANGE_TEMPLATE.md, then this plan completely.
Execute ONE work package (WP) per branch and PR, in the order of section 5. For every WP:
 1. Re-verify the "Current (verified)" claims; if they differ, stop and record it in BLOCKERS.md.
 2. Write the failing test first, then implement the smallest change that satisfies "Done when".
 3. Run the WP's commands plus: ./gradlew testDebugUnitTest lintDebug assembleDebug
    and the docs commands in section 3. Record exact commands/outputs; mark anything not run as NOT RUN.
 4. Update docs in the same PR (FEATURE_MATRIX, docs/features/*, CHANGELOG, behavior contracts).
 5. Write the PR summary required by AGENTS.md (requirements, paths, impact, evidence, privacy
    assessment, migration, docs, uncertainties, rollback).
Never implement anything in section 2 whose decision is not marked "Approved: yes" by the owner.
Never copy code, strings, icons or layouts from blockads-android (GPL-3.0). Ideas only.
```

---

## 1. Ground rules (non-negotiable)

1. **Project constraints** in `AGENTS.md` stay in force: no telemetry/analytics/crash SDK, no accounts, no raw-domain learned identifiers, **no TLS interception or user CAs**, no broader routing, **no public DNS fallback on the active resolver path** without an explicit decision, no change to `DecideBlockingUseCase` precedence or `APPROVED_ONLY` semantics as a side effect.
2. **License hygiene (important).** blockads-android is **GPL-3.0**; un-blocker is **Apache-2.0**. Copying or closely translating GPL code, resources, strings or icons into this repo would conflict with Apache-2.0. Implement every feature **clean-room from the written spec below and from public Android documentation**. Each PR states: "No code, strings, icons or layouts were copied from third-party GPL projects." (Not legal advice; if in doubt the owner should consult a lawyer.)
3. **Privacy:** every WP has a "Privacy/security" line. Anything that stores new plaintext, adds a permission, exports a component, or opens a network connection needs it filled in honestly.
4. **Compatibility:** min SDK stays **26** unless D5 is approved. Room changes are **non-destructive migrations** with a migration test. No private-data backup (`allowBackup` stays off).
5. **UI rules** (`.agents/rules/ui-compose.md`): keep the Material 3 theme, adaptive layout, **floating-pill navigation**, One UI-style cards and the reduced-motion primitives. State must reflect real VPN coverage, consent and errors.
6. **One logical change per PR**, feature branch, no CI weakening, no signing-policy change (`docs/release-signing-and-upgrade-policy.md`).

---

## 2. Decision records (owner fills in before the agent touches them)

| ID | Question | Recommendation | Approved? | If approved, also edit |
|---|---|---|---|---|
| **D1** | Allow an **optional user-selected upstream resolver** (custom IP, DoH, DoT)? | Approve as **opt-in, default OFF, fail-closed, no bundled default provider** | `Approved: NO` | `AGENTS.md` #7, `PRIVACY.md`, `docs/DNS_PIPELINE.md` |
| **D2** | Store **user-authored** allow/block rules in a **readable** form (like *My domains*) so they can be listed and removed? Learned reputations stay HMAC-only. | Approve (user-authored content, private no-backup storage, same precedent as *My domains*) | `Approved: YES` (2026-11-28 owner decision) | `AGENTS.md` #6, `PRIVACY.md`, `docs/features/USER_RULES.md` |
| **D3** | Allow **SSID-based** trusted-network rules (needs location permission)? | **Do not approve.** Use transport-only rules (WP-43) instead | `Approved: NO` | manifest, `PRIVACY.md` |
| **D4** | Build an **in-app WebView "Shield Browser"** with URL and cosmetic filtering (no CA, no MITM)? | Do the **spike only** (WP-61a), decide after its report | `Approved: NO` | `AGENTS.md` #2 wording, `PRODUCT.md` |
| **D5** | Lower **min SDK 26 → 24**? | **Do not approve** (small gain, API-26 notification/FGS compat cost) | `Approved: NO` | `docs/CONFIGURATION.md` |

**Not planned (decided here; override only with a new decision record):**

| blockads-android feature | Why it is not in this plan |
|---|---|
| HTTPS filtering / per-app MITM / CA install | Directly forbidden by `AGENTS.md` #2 and the project's DNS-only promise. The *Beyond DNS* hub already gives companion guidance. Shield Browser (D4) is the CA-free alternative. |
| Root proxy mode (iptables) | Broadens routing, root-only, security-sensitive; tiny audience. |
| WireGuard / Tailscale / Shadowsocks tunnels | A VPN-client product, not a DNS filter; large maintenance surface; conflicts with "not an anonymity VPN". |
| Per-app internet **firewall** | Needs full-tunnel packet handling (routing change). Per-app **exclusion from filtering** is planned (WP-23). |
| Opt-in crash reporting SDK (Sentry) | `AGENTS.md` #1 forbids analytics/crash SDKs. A **manual local log/diagnostic export** is planned instead (WP-22). |
| Android TV app (`blockadstv`) | Separate product surface; revisit after v3.0.0 if wanted. |
| Go userspace network stack | Not needed for DNS-only filtering. |

---

## 3. Commands used by every WP

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug          # always
./gradlew connectedDebugAndroidTest                          # if platform/UI paths changed, on a device/emulator
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
python3 scripts/docs_sync.py --write && python3 scripts/docs_contract.py && python3 scripts/docs_sync.py --check
```
Commit regenerated `docs/generated/*`. Add a dated `CHANGELOG.md` entry per release.

---

## 4. Gap analysis (what is missing, verified)

| Capability | blockads-android | un-blocker 2.7.2 (verified) | WP |
|---|---|---|---|
| Restart VPN after **app update** | Handled (package-replaced receiver) | `BootReceiver` handles only `BOOT_COMPLETED` and `QUICKBOOT_POWERON`; no `MY_PACKAGE_REPLACED` | WP-01 |
| Per-app attribution while learning | n/a | Learn filters by **UID** (`RecordingSessionManager.recordDomain`: mismatch dropped, `null` accepted); VPN builder only calls `addDisallowedApplication(own package)` (`UnblockerVpnService.kt:242`); README claims "isolated" recording | WP-02 |
| Learn review defaults | n/a | Substring categorizer; pre-selected actions; whole-site allow (`LearnViewModel.categorizeDomain`) | WP-03 |
| View/remove my rules | Custom allow/block lists, editable | Rules stored as HMAC identifiers; **cannot be listed**; only "Clear all" or re-enter the domain (README) | WP-20 |
| Explain a decision | Per-list stats | `DomainStatEntity.lastReason` exists in Room but is **not shown or exported** | WP-21 |
| Export | Export logs + settings backup/restore | `DomainExporter` writes **7 columns** only; no settings backup | WP-22 |
| Per-app exclusion | Per-app VPN bypass | None | WP-23 |
| Quick Settings tile / widget / shortcuts / Tasker | Yes | **None** (no `TileService`, `AppWidgetProvider`, shortcuts) | WP-30..33 |
| Hide from recents | Yes | No | WP-34 |
| Statistics screen | Yes | No (counts only in logs) | WP-40 |
| Onboarding | Full flow | `SystemHardeningWizard` only | WP-41 |
| Trusted networks | SSID-based (location permission) | None | WP-43 (transport only) |
| Localization | 21 locales | English only; `strings.xml` is 4 lines; **≥144 hardcoded UI literals** | WP-12, WP-53 |
| Dependencies | Compose BOM 2026.11.01, Room 2.8.5 | Compose BOM **2024.10.00**, Room **2.6.1**, Hilt 2.51.1, Kotlin 2.0.21, AGP 8.10.1, target 35 | WP-10 |
| Navigation | Navigation component | Manual `selectedTab` state in `MainScreen.kt`; Extensions is an overlay | WP-11 |
| Settings | Sectioned | One 830-line `SettingsScreen` composable | WP-13 |
| Theme choice | System/Light/Dark, 7 accents, Material You | `dynamicColor` parameter exists in `Theme.kt`; **no user setting** | WP-50 |
| F-Droid / IzzyOnDroid | Listed | GitHub APK only | WP-35 |
| Custom upstream / DoH | Yes (provider list) | System resolvers only, fail-closed | WP-60 (D1) |
| Cosmetic / URL filtering | MITM | None | WP-61 (D4) |

---

## 5. Roadmap and release plan

| Phase | Release | Work packages | Theme |
|---|---|---|---|
| 0 | **v2.7.3** hotfix | WP-00, WP-01, WP-02, WP-03 | Correctness before features |
| 1 | **v2.8.0** | WP-10, WP-11, WP-12, WP-13 | Foundation (deps, navigation, i18n, settings IA) |
| 2 | **v2.9.0** | WP-20, WP-21, WP-22, WP-23, WP-24 | Control and transparency |
| 3 | **v2.10.0** | WP-30, WP-31, WP-32, WP-33, WP-34, WP-35 | Access, automation, distribution |
| 4 | **v2.11.0** | WP-40, WP-41, WP-42, WP-43 | Insight and onboarding |
| 5 | **v3.0.0** | WP-50, WP-51, WP-52, WP-53 | UX polish, accessibility, languages |
| 6 | gated | WP-60, WP-61, WP-62, WP-63 | Only if D1/D4/D3/D5 approved |

---

## 6. Work packages

Template per WP: **Goal · Current (verified) · Change · Files · Data · Privacy/security · Tests · Docs · Done when · Size/Risk.**

### Phase 0 · v2.7.3 hotfix

#### WP-00 · Baseline verification pass (S)
- **Goal:** make docs and behavior agree before adding features.
- **Change:** (1) Add `app/src/test/resources/must-block-hosts.tsv` and `AdHostsMustBlockTest`: with no user rules, SMART mode must block `googleads.g.doubleclick.net`, `pagead2.googlesyndication.com`, `tpc.googlesyndication.com`, `static.doubleclick.net`, `pubads.g.doubleclick.net`, `ad.doubleclick.net`, `fundingchoicesmessages.google.com`, `ogads-pa.clients6.google.com`, `imasdk.googleapis.com`, `mobile.events.data.microsoft.com`. README currently says several of these are "actively blocked"; earlier device exports showed them **allowed** by an override. (2) Add a `docs/qa/2026-11-baseline.md` listing every README/FEATURE_MATRIX claim you could not confirm in code or tests. (3) Do not change behavior in this WP.
- **Files:** `app/src/test/...`, `docs/qa/`. **Tests:** the new test must pass, or its failure is itself the finding (report it).
- **Done when:** report written; failing claims are listed, not silently edited.

#### WP-01 · Restart protection after app update (S) · F25
- **Current:** `AndroidManifest.xml` `BootReceiver` filter = `BOOT_COMPLETED` + `QUICKBOOT_POWERON` only. Android stops a VPN when the app is replaced.
- **Change:** add `android.intent.action.MY_PACKAGE_REPLACED` to the receiver filter; in `BootReceiver` reuse the existing boot logic (honor `autoRestartOnBoot` and persisted protection intent, require prior VPN consent via `VpnService.prepare(context) == null`, never prompt from a receiver).
- **Privacy/security:** no new permission; receiver stays `exported="false"` (system broadcasts are delivered regardless).
- **Tests:** `BootReceiverTest` (boot, quickboot, package-replaced, consent missing, intent disabled). **Done when:** after `adb install -r` the VPN resumes if it was on (device check, record result).

#### WP-02 · Truly isolated per-app Learn recording (M) · amends Learn feature
- **Current:** attribution by UID (`UidResolver`); `recordDomain` drops UID mismatches and accepts `null`, so results are wrong either way. The TCP-DNS call site passes mismatched local/remote addresses (`UnblockerVpnService.kt` ~L290 and ~L416, *verify*).
- **Change:** when a session starts, rebuild the VPN with `Builder.addAllowedApplication(targetPackage)` and **no** `addDisallowedApplication` (they cannot be mixed). All DNS then seen is from that app by construction; remove UID filtering from recording (keep `UidResolver` for diagnostics only). Show a banner "Learning <app>. Other apps are not filtered until you stop (max 10 min)". Restore the normal builder in `finally`, on timeout, on service crash and on process death (persist `isolatedSessionActive`, check at service start). If no DNS is seen for 60 s show "App may be idle or use its own encrypted DNS" with a link to `docs/dns-bypass-matrix.md`.
- **Tests:** `RecordingSessionManagerTest` (everything seen is recorded, nothing UID-filtered), VPN restore tests (finally/timeout/crash), `VpnBuilderPolicyTest` (never both allowed and disallowed lists).
- **Privacy:** domains stay in the existing session store; no new persistence. **Done when:** a device session on two different apps returns disjoint domain sets.

#### WP-03 · Learn review defaults and safe allow (M)
- **Current:** `LearnViewModel.categorizeDomain` uses substring `contains(...)` for tracker words and package segments; FIRST_PARTY pre-selects ALLOW whole site; CDN rows default to ALLOW.
- **Change:** (1) Categorize on **whole DNS labels** and registrable domains (use the shared public-suffix cache). `threads.net`, `downloads.example.com`, `broadcast.example.com` must **not** be tracker/first-party matches. (2) **Nothing is pre-selected for BLOCK.** The checkbox is the user's explicit approval; add an "Add all list matches" button. (3) ALLOW from review is **exact host**; "whole site" is a per-row toggle, disabled for shared platforms (PSL private section: `amazonaws.com`, `cloudfront.net`, `googleapis.com`, `github.io`, …). (4) Refuse (with explanation and a second explicit tap) any ALLOW whose host or registrable domain matches an enabled block rule or the shipped ad seed (`doubleclick.net`, `googlesyndication.com`, `googleadservices.com`, `2mdn.net`, `adnxs.com`, …). (5) Show "Also seen in: <apps>" using stored sessions.
- **Tests:** `LearnCategorizerTest` (positive and negative whole-label cases), `LearnApplyRulesTest` (no BLOCK without checkbox; ALLOW exact; ad-domain allow refused), regression for the example hosts above.
- **Done when:** approving a Google app session cannot create an allow that covers `doubleclick.net`.

---

### Phase 1 · v2.8.0 foundation

#### WP-10 · Platform and dependency refresh (M-L, risk medium)
- **Current:** AGP 8.10.1, Kotlin 2.0.21, Compose BOM 2024.10.00, Hilt 2.51.1, Room 2.6.1, core-ktx 1.13.1, compile/target SDK 35.
- **Change (separate PRs, in this order):** (a) Compose BOM + Material3 → current stable, then fix visual regressions; (b) Room → current 2.8.x with schema JSON diff review and migration tests; (c) Hilt/KSP/Kotlin and AGP; (d) `compileSdk`/`targetSdk` 36 after reading Android 16 behavior changes (large-screen orientation/resizability rules, predictive back) and adapting `MainActivity` and back handling. For reference, blockads-android builds with BOM 2026.11.01 and Room 2.8.5; verify the latest stable at execution time and follow `.agents/rules/gradle-dependencies.md` and `docs/DEPENDENCIES.md`.
- **Tests:** full unit suite, `connectedDebugAndroidTest` on API 26, 34, 36 emulators, lint. **Done when:** green CI, no new lint baseline entries, `THIRD_PARTY_NOTICES.md` updated.

#### WP-11 · Navigation Compose + deep-link router (M) · F26
- **Current:** `MainScreen.kt` keeps `selectedTab` and an `isExtensionsOpen` flag; four `NavItem`s (Home, Learn, Logs, Settings).
- **Change:** adopt `androidx.navigation:navigation-compose` with type-safe routes; keep `FloatingPillNavBar` as the bottom bar; make Extensions, Exceptions, Statistics, Onboarding, About real destinations; preserve per-tab state; proper back stack; predictive-back ready. Add an internal deep-link router (`unblocker://home`, `://logs?domain=<d>`, `://learn`, `://settings/<section>`, `://exceptions`, `://stats`) used later by tile, shortcuts, widget and notification. `unblocker://start` only **opens** the app (never starts silently).
- **Security:** deep links are handled in-app only; `MainActivity` exports only MAIN/LAUNCHER plus the `unblocker` scheme VIEW filter; validate every parameter.
- **Tests:** `NavGraphTest` (route table, back stack), `DeepLinkParserTest` (malformed, oversized, injection), Compose UI test for tab switching with state kept.
- **Done when:** no manual `selectedTab` remains; all current screens reachable and back works.

#### WP-12 · String externalization and i18n guard (M-L) · F27
- **Current:** `res/values/strings.xml` is 4 lines; ≥144 hardcoded literals in `ui/**`, plus strings in services (notifications) and toasts.
- **Change:** move every user-visible string to `strings.xml` (`<screen>_<element>` names, plurals for counts, formatted args instead of concatenation, no sentence fragments). Notification texts, toasts, dialogs, `contentDescription`s included. Add `scripts/check_hardcoded_strings.py` (flags `Text("…")`, `text = "…"`, `title = "…"`, `contentDescription = "…"` in `app/src/main`, with a small allowlist) and run it in `kotlin-style.yml`. Enable lint `MissingTranslation`/`HardcodedText` as errors for resource XML. Set `android:supportsRtl="true"` and test with pseudo-locales `en-XA` and `ar-XB`.
- **Tests:** script unit test in `scripts/tests/`; a Compose UI test that renders each screen in `ar-XB` without crashing. **Done when:** the script reports 0 violations and the app looks unchanged in English.

#### WP-13 · Settings information architecture (M) · F28
- **Change:** split the 830-line `SettingsScreen` into a grouped list with sub-screens and a search field: **Protection** (mode, tier, presets, adult filter, pause) · **Filters** (Extensions, custom lists, update schedule) · **Rules & exceptions** (My domains, exceptions, rule tester, excluded apps) · **Learning** (Learn, reset learned) · **Privacy & data** (logging pause, history mode, retention, export/backup, hide from recents, clear data) · **Automation** · **Appearance** · **System health** (hardening checklist, Private DNS, battery) · **About**. Each row has a title, one-line description and live state. Keep all existing preferences and keys; no behavior change.
- **Tests:** Compose UI tests per section; preference-key regression test. **Done when:** every previous setting is reachable and nothing is lost.

---

### Phase 2 · v2.9.0 control and transparency

#### WP-20 · Exceptions screen and rule tester (M-L) · F29 · needs D2 for the readable list
- **Current:** `PrivateDomainSet` stores allow/block rules as HMAC identifiers; the app cannot show them. This hid an over-broad allow for days in real exports.
- **Change (no decision needed):** (1) **Rule tester**: type a domain, see the decision and reason (allowed by user exception / blocked by list X / protected / no match) and offer "Remove exception". (2) "N legacy exceptions (hidden)" count plus **Reset all exceptions** with a confirm dialog (does not touch lists, logs or *My domains*). (3) When logs are cleared, say "Exceptions are kept (N)".
- **Change (if D2 approved):** add a private no-backup `user_rule(domain, action, scope, created_at, source)` table for **user-authored** rules; list, search, filter and delete them; migrate nothing from hashed storage (cannot be recovered). Learned reputations stay HMAC-only.
- **Health warning:** at service start and after each Learn apply, if an ALLOW covers an ad/tracker domain in the shipped seed or an enabled list, show a dashboard banner "You allowed ad domains: …" linking to the screen.
- **Tests:** `RuleTesterTest`, `ExceptionHealthTest` (allow on `doubleclick.net` triggers it), migration test, UI test for the reset dialog. **Done when:** a user can find and remove an exception without clearing app data.

#### WP-21 · Decision explainer ("Why?") (M) · F30
- **Current:** `DomainStatEntity.lastReason` exists but is not shown. Decisions carry `reasonCode` (`USER_ALLOW`, …).
- **Change:** persist per domain: last outcome (BLOCKED_USER, BLOCKED_RULE, BLOCKED_LEARNED, BLOCKED_ADULT, BLOCKED_CNAME, ALLOWED_USER, ALLOWED_PROTECTED, ALLOWED_NO_MATCH, **UPSTREAM_FAIL**), matched rule kind/value (when a list rule), source list id, upstream-fail count, last network type (WIFI/CELLULAR/OTHER). Record `UPSTREAM_FAIL` wherever the service answers SERVFAIL (today a DNS failure is indistinguishable from a block). **No SSID, IP, BSSID or account data.** Show a "Why?" line in Logs rows and in `ConnectionHistoryDialog`. Append enum values; keep stored ordinals stable.
- **Data:** Room migration adding columns with defaults; do not drop history. **Tests:** `QueryLogSinkTest`, migration test, `UnblockerVpnServiceTest` (SERVFAIL path records `UPSTREAM_FAIL`). **Done when:** every blocked row explains itself.

#### WP-22 · Export v2 and settings backup/restore (M) · F31
- **Current:** `DomainExporter` HEADERS = Domain, LastDecision, TotalQueries, BlockedQueries, AllowedQueries, FirstSeen, LastSeen.
- **Change:** (1) `domains.csv` columns: `domain, registrable_domain, outcome, reason, rule_kind, rule_value, source_list_id, source_list_name, user_override, protected, total_queries, blocked_queries, allowed_queries, upstream_fail_queries, first_seen, last_seen, network_types`. Keep RFC 4180 escaping and formula-injection defense. (2) Export dialog: *All / Blocked only / Blocked + failed + overrides*, optional "Redact to registrable domain", clear privacy warning. (3) `export-meta.json` sidecar: app version/code, mode, toggles, per-list id/enabled/ruleCount/lastUpdated/lastStatus, never-block asset SHA-256, row counts. No device or personal identifiers. (4) **Settings backup/restore** (SAF `CreateDocument`/`OpenDocument`): preferences, enabled lists, custom list URLs, excluded apps, user rules **only if D2 approved** (warn that rules are readable in the file). Never include learned reputations or logs. Validate and bound the import (size cap, schema version, unknown keys ignored). (5) **Diagnostics file** (manual, local): version, device model/API, VPN state, resolver count, last errors; no domains. This replaces a crash-reporting SDK.
- **Tests:** golden-file CSV test, CSV injection/quote/unicode tests, redaction test, 10k-row export without OOM, backup round-trip, malicious-backup fuzz cases. **Done when:** export → import round-trips settings and the CSV explains each decision.

#### WP-23 · Excluded apps (per-app filtering bypass) (M) · F32
- **Change:** a picker listing **launchable apps** (uses the existing `<queries>` LAUNCHER intent; **no `QUERY_ALL_PACKAGES`**) with search, "show system apps" toggle and a cap (200). Selected packages are added with `addDisallowedApplication` (plus own package). Changes rebuild the builder with a short debounce. Show a clear warning: "Excluded apps' DNS is not filtered." Isolated Learn sessions ignore exclusions. Store package names in no-backup preferences (package names are not browsing data).
- **Tests:** `VpnBuilderPolicyTest` (exclusion applied, no mixing with allowed list), picker ViewModel tests, process-death restore. **Done when:** an excluded app's domains no longer appear in logs.

#### WP-24 · Logs domain detail sheet and actions (M)
- **Current:** Logs already have search and filter chips plus `ConnectionHistoryDialog`.
- **Change:** tapping a row opens a bottom sheet: decision, **Why?** (WP-21), counts, first/last seen, mini history, and actions **Allow / Block / Add to My domains / Remove exception / Copy / Share**. Multi-select for bulk actions. Allow on an ad-list domain follows WP-03's confirmation rule. Add a "DNS failed" chip. Sticky date headers; stable `LazyColumn` keys.
- **Tests:** ViewModel action tests, UI test for the sheet. **Done when:** a user can go from a log row to a rule in two taps.

---

### Phase 3 · v2.10.0 access, automation, distribution

#### WP-30 · Quick Settings tile (S-M) · F33
- **Change:** `UnblockerTileService : TileService` (`android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, exported as required). States: ACTIVE (protecting), INACTIVE, UNAVAILABLE (consent missing). Tap: if `VpnService.prepare` needs consent, open `MainActivity` via a `PendingIntent` (use `startActivityAndCollapse(PendingIntent)` on API 34+); otherwise start/stop through `QuickStartManager`. Subtitle shows mode or "Paused". Observe `QuickStartManager.status`.
- **Tests:** tile state-mapping unit tests; device check on API 26 and 34. **Privacy:** no new permission.

#### WP-31 · App shortcuts (S) · F34
- **Change:** static shortcuts `Pause 5 min` and `Learn an app`, plus a dynamic "Resume" when paused. Routes through WP-11 deep links; shortcuts never silently start the VPN without prior consent.
- **Tests:** shortcut XML lint, router tests.

#### WP-32 · Home-screen widget (M) · F35
- **Change:** a small widget (Jetpack Glance, or `RemoteViews` if the owner rejects a new dependency): status, one toggle, and **blocked today** once WP-40 exists (until then status + toggle only). Updates on state change, not on a timer. Respects reduced motion; light/dark.
- **Tests:** state-mapping unit tests; device check. **Dependencies:** if Glance is added, update `docs/DEPENDENCIES.md` and notices.

#### WP-33 · Automation intents (opt-in, token-protected) (M) · F36 · *security-sensitive*
- **Change:** Settings → Automation: toggle **OFF by default**. When enabled, a receiver accepts `com.unblocker.app.action.START|STOP|PAUSE|RESUME` with a required `token` extra (128-bit random value generated on enable, stored in no-backup storage, shown to the user with Copy/Regenerate). Constant-time comparison, rate limit (e.g. 1 call per 2 s), reject when disabled. `START` works **only** if consent already exists; otherwise post a notification to open the app. The receiver is exported **only while enabled** (use a component-enabled-setting toggle). Never log tokens.
- **Tests:** accept/reject token, rate limit, disabled state, missing consent, intent-spoofing cases. **Done when:** a Tasker-style broadcast with the correct token pauses protection; any other caller is ignored.

#### WP-34 · Hide from recents and notification polish (S) · F37
- **Change:** *Privacy & data → Hide from recent apps* using `ActivityManager.AppTask.setExcludeFromRecents`. Notification: add **Resume** when paused; show "Blocked today: N" after WP-40; keep Pause and Stop actions; make channel importance low.
- **Tests:** pref wiring unit test; manual device check.

#### WP-35 · F-Droid / IzzyOnDroid readiness (M) · F38
- **Change:** add `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt`, `changelogs/<versionCode>.txt` and phone screenshots; verify there are no proprietary/Google-only dependencies; check reproducible-build determinism (two clean builds produce identical unsigned APKs, or document differences); add `docs/FDROID_SUBMISSION.md` with a draft metadata YAML and anti-feature review. **Do not change the signing policy or workflows to "make it pass".** Submission itself is the owner's action.
- **Done when:** the checklist in the doc is complete and a clean build is reproducible or the gaps are listed.

---

### Phase 4 · v2.11.0 insight and onboarding

#### WP-40 · Statistics screen (M-L) · F39
- **Data:** new Room table `daily_stat(day_epoch PK, total, blocked, allowed, failed)`, updated in `QueryLogSink` batches, 30-day retention (same as aggregates), honors *logging paused*. Non-destructive migration + test.
- **UI:** 7/30-day bar chart (Compose Canvas, no chart dependency), block rate, today's totals, top 10 blocked domains (from `domain_stats`, labeled "within retention"), per-list hit counts when WP-42 exists. Empty state for new installs.
- **Tests:** rollup tests (midnight boundary, timezone change), migration, UI test. **Privacy:** counts only.

#### WP-41 · Onboarding flow with preset picker (M) · F40
- **Steps (skippable, re-runnable from About):** 1) promise and privacy summary · 2) VPN consent explanation · 3) notification permission · 4) battery-optimization exemption (permission already declared) · 5) **preset picker** (Essential / Recommended / Aggressive, with the OEM suggestion that exists) + **regional list suggestion** from device locale (suggest only; never auto-enable; map locale→regional list ids from `extensions_manifest.json`, do not hard-code) · 6) Private DNS / hardening check (reuse `SystemHardeningWizard`) · 7) single **START** button. Persist `onboardingCompleted`.
- **Tests:** ViewModel state machine, process-death resume, UI test. **Done when:** a fresh install reaches protection in ≤ 7 taps without leaving the flow unexpectedly.

#### WP-42 · Per-list hit counters (M) · F41
- **Change:** record the `sourceId` of the matched rule (already carried by `DnsRule`) into daily per-list counters; show "blocked N today" per list in the Extensions screen and use it to hint at unused or noisy lists. Counts only, no domains.
- **Tests:** attribution tests (first-match vs all-match decision documented), migration.

#### WP-43 · Transport-based auto-pause (M) · F42 *(replaces SSID trusted networks, D3)*
- **Change:** optional rules "Pause filtering on Wi-Fi" / "on mobile data" using `ConnectivityManager.NetworkCallback` transport types (**no location permission**). Pause means decisions return ALLOW(PAUSED), the VPN stays up. Show "Auto-paused (Wi-Fi)" on Home and in the tile.
- **Tests:** callback state machine (Wi-Fi↔cellular flaps, multiple networks), UI test.

---

### Phase 5 · v3.0.0 UX polish, accessibility, languages

#### WP-50 · Theming (S-M) · F43
- **Change:** Appearance settings: Theme (System / Light / Dark / **AMOLED black**), **Dynamic color** switch (API 31+, uses the existing `dynamicColor` parameter), accent choice (keep Teal default plus 4-6 curated accents). Persist in preferences; apply without restart. Keep One UI-style cards and the floating pill.
- **Tests:** theme-state unit tests; contrast check on every accent (WCAG AA for text on surfaces).

#### WP-51 · Accessibility pass (M)
- **Checklist (each item gets a Compose semantics test where possible):** every interactive element has a role and a localized `contentDescription`; touch targets ≥ 48dp; toggles expose `Role.Switch` and state; focus order and headings (`heading()` semantics); no information by color alone (blocked/allowed use icon + text); font scale 1.0/1.3/2.0 without clipped text; TalkBack announces VPN state changes (live regions); respects reduced motion (`ReducedMotionPolicy`); dialogs trap focus. Run Accessibility Scanner and record results in `docs/qa/`.

#### WP-52 · Adaptive layouts and performance (M)
- **Change:** use window size classes: compact keeps the floating pill; medium/expanded use a navigation rail and list-detail panes for Logs and Settings. Review recomposition (stable keys, `remember`, immutable UI models). Optional: baseline profile via a Macrobenchmark module; **record measured before/after numbers with the exact command, never invented ones.**
- **Tests:** UI tests at 360dp, 600dp, 840dp widths; manual fold/rotate check.

#### WP-53 · Localization release (M-L) · F44
- **Change:** `res/xml/locales_config.xml` + per-app language setting (`AppCompatDelegate.setApplicationLocales`, verify behavior with the `ComponentActivity` host). Ship **Hindi (hi)** first, then the locales blockads-android offers (ar, cs, de, es, fr, hu, in, it, iw, ja, kk, ko, pl, pt, pt-BR, ru, th, tr, uk, vi, zh) as the owner chooses. Translations must be **human-reviewed**; machine-drafted strings are marked `tools:ignore` + a `docs/i18n/REVIEW.md` checklist and are not shipped as final. Localize dates/numbers with locale-aware formatters; keep technical terms (DNS, VPN) consistent via a glossary in `docs/i18n/GLOSSARY.md`.
- **Tests:** lint `MissingTranslation` clean, pseudo-locale smoke, per-app language persistence.

---

### Phase 6 · gated (only with the matching decision approved)

#### WP-60 · Optional custom upstream resolver (D1) (L, risk high)
- **Design:** introduce an `UpstreamStrategy` abstraction in `DnsUpstreamClient`: `SystemResolvers` (default, today's behavior) · `CustomResolver(ip[:port])` · `Doh(url)` · `Dot(host)`. Opt-in screen with a plain-language disclosure ("this provider will see your DNS queries"). **No bundled default provider**; a suggestion list is allowed only as clearly labeled presets the user selects. Bootstrap by IP or system resolution **before** the VPN covers that lookup, using protected sockets so queries never loop through the tunnel. On failure: **fail closed** by default; "fall back to system resolvers" is a separate explicit toggle. Validate responses with the existing `DnsResponseValidator`; bound sizes and timeouts; TLS verification mandatory; no certificate-pinning bypass.
- **Tests:** fake DoH/DoT server tests (success, TLS failure, timeout, truncated/malformed answer, redirect), no-loop test, fail-closed test, `NetworkCoverage` doc update.
- **Docs:** `AGENTS.md` #7, `PRIVACY.md`, `docs/DNS_PIPELINE.md`, `docs/dns-bypass-matrix.md`.

#### WP-61 · Shield Browser (D4): spike, then implement (L)
- **61a spike (S-M, no product code merged):** a prototype WebView that applies (1) request blocking in `shouldInterceptRequest` using the already-bundled domain rules and (2) cosmetic hiding from a **new, separately parsed** cosmetic-rule subset (element-hiding only, no scriptlets). Report: coverage on 20 sample pages, page-load cost, maintenance burden, privacy review (no TLS interception; WebView sees its own traffic only). Write `docs/qa/shield-browser-spike.md`.
- **61b (only if approved after the report):** minimal browser (URL bar, tabs optional, private mode, clear data), no history upload, settings toggle, own list-update rules.

#### WP-62 · SSID trusted networks (D3, not recommended) (M)
- Requires `ACCESS_FINE_LOCATION` runtime consent, a rationale screen and a hashed network-ID store. Implement only after WP-43 proves insufficient.

#### WP-63 · Min SDK 24 (D5, not recommended) (M)
- Audit APIs and notification/foreground-service paths; add compat shims and run the device matrix on API 24/25 before deciding.

---

## 7. UI/UX specification

**Principles:** privacy visible (say what stays on the device), one obvious primary action, honest state (never show "Protected" if coverage is partial), reversible actions, no dark patterns, every setting explains its effect.

**Target navigation map**
```text
Home ─ Learn ─ Logs ─ Settings            (floating pill, compact widths)
Home → Statistics, Exceptions banner, Onboarding (first run)
Logs → Domain sheet → Allow/Block/Why
Settings → Protection | Filters(Extensions) | Rules & exceptions | Learning | Privacy & data
          | Automation | Appearance | System health | About
```

**Home (one primary action, as today):**
```text
┌───────────────────────────────┐
│  Protected (system DNS)   ⚙   │  status hero: real coverage text (VPN, Private DNS leak, paused)
│        [  STOP / START  ]     │  single primary button
│  Mode: SMART   Tier: Optimal  │  chips (tap to change)
├───────────────────────────────┤
│  Today  1,284 blocked · 21%   │  sparkline → Statistics
├───────────────────────────────┤
│  ⚠ Private DNS bypasses filter │  attention cards (hardening, battery, exceptions warning)
│  ⚠ You allowed ad domains      │
├───────────────────────────────┤
│  Pause 5 min · Learn an app    │  quick actions
└───────────────────────────────┘
```
**Logs:** search + chips (All / Blocked / Allowed / Failed / Overrides), row = domain, decision icon+text, count, last seen; tap → detail sheet with **Why?** and actions.
**Exceptions:** tabs *Allow* / *Block*, search, swipe to remove, banner for hidden legacy rules, "Test a domain" field at the top.
**Learn:** stepper (Pick app → Use it → Review → Apply), nothing pre-selected, "Also seen in" chips, clear banner while other apps are unfiltered.
**Statistics:** 7/30 toggle, bar chart with accessible text summary, top blocked list, per-list counts.
**Onboarding:** 6 short pages with Back/Skip, progress dots, no dark patterns on permissions.
**Empty/error states:** no logs yet, consent revoked, VPN revoked by another VPN, Private DNS strict, resolver list empty (fail-closed explanation with a Retry), list download failed (show per-list status and last good copy).
**Motion/haptics:** reuse `motion/` primitives; honor reduced motion; light haptic on toggle only.
**Copy rules:** plain language, no unsupported claims (never invent blocking/battery percentages; see README).

---

## 8. Data and migration summary

| Change | WP | Migration |
|---|---|---|
| `domain_stats` new columns (outcome, rule, source, fail count, network type) | WP-21 | Room auto-migration or manual with defaults + test |
| `daily_stat` table | WP-40 | new table + test |
| `user_rule` table (only if D2) | WP-20 | new table; hashed rules not migrated |
| Preferences: onboardingCompleted, theme, dynamicColor, accent, excluded apps, automation token/toggle, auto-pause rules, hideFromRecents | various | additive keys; defaults preserve current behavior |

Schema JSONs in `data-store/schemas/` are committed with each change.

---

## 9. Test strategy

- **Unit (JVM):** every WP's listed tests; table-driven `DecideBlockingUseCaseTest` cases are added, never loosened.
- **Instrumented/Compose:** the repo has only 3 `androidTest` files; add UI tests for navigation, settings sections, exceptions, logs sheet, onboarding, accessibility semantics.
- **Device matrix** (`android-device-matrix.yml`): API 26, 34, 36 plus a Samsung One UI device by hand (the owner's primary device).
- **Manual script per release** in `docs/qa/<release>.md`: install over the previous version (VPN resumes), START, browse 5 min, export, import settings, tile/shortcut/widget, TalkBack pass, language switch.
- **Security review** (`/privacy-security-review`) for WP-20, 22, 33, 60, 61.

---

## 10. Definition of done (every PR)

- Failing-test-first evidence, all listed tests green, `testDebugUnitTest lintDebug assembleDebug` run (or `NOT RUN` with reason).
- No change to decision precedence unless explicitly approved; protected-domain and `must-block` tests green.
- Privacy/security section completed; no new plaintext, permission, exported component or network call unless the WP says so.
- Docs updated and `docs_sync`/`docs_contract` clean; `CHANGELOG.md`, `FEATURE_MATRIX.md` (F25+), feature docs.
- PR summary with evidence, migration notes and rollback.

---

## 11. Risk register

| Risk | Mitigation |
|---|---|
| GPL contamination from reference app | Clean-room rule (section 1.2), PR attestation, no resource copying |
| Dependency upgrades break UI/Room | One upgrade per PR, schema diff review, device matrix |
| i18n refactor touches every screen | Do WP-12 before new UI; CI guard prevents regressions |
| Exported components (tile, automation) widen attack surface | Token, opt-in, component toggling, tests, security review |
| Isolated Learn leaves other apps unfiltered if restore fails | `finally` + watchdog + persisted flag + startup check |
| Readable rule store (D2) leaks via backup/export | no-backup storage, explicit export warning, excluded from default exports |
| Over-promising ad blocking | README/UI copy rules; YouTube/Meta in-stream ads share hosts with content and cannot be separated by DNS |

---

## 12. Owner checklist (filled 2026-11-28)

- [x] D1 … D5 decisions recorded in section 2 (D2 **YES**; D1/D3/D4/D5 **NO** — Phase 6 stays gated).
- [x] Locales to ship in WP-53: Externalize to `strings.xml` + pseudo-locales (`en-XA`, `ar-XB`) first, then Hindi (`hi`) flagged with `tools:ignore` for human review.
- [x] Glance dependency allowed for the widget (WP-32)? **NO** — use standard Android `RemoteViews` with zero new dependency.
- [ ] Reviewer for translations (pending human reviewer assignment; machine-drafted `hi` strings marked for review).
- [x] Release cadence agreed: Sequential execution across Phases 0–5 (one feature branch & PR per WP, releasing per phase).
- [x] Confirmed `Not planned` list (section 2) is acceptable.
