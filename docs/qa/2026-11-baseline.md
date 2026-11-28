# Baseline Verification Report — Un-Blocker v2.7.2 (`1dfd0c5`)

**Date:** 2026-11-28
**Work package:** WP-00 (`Baseline verification pass`)
**Scope:** Static source inspection and JVM unit verification against `main` @ `1dfd0c5`. No runtime behavior was altered in WP-00, and **no physical/emulator device run was executed (`connectedDebugAndroidTest`: NOT RUN — no attached device/running emulator)**.

---

## 1. `must-block-hosts.tsv` Corpus Verification (`AdHostsMustBlockTest`)

Evaluated all 10 required hosts (`googleads.g.doubleclick.net`, `pagead2.googlesyndication.com`, `tpc.googlesyndication.com`, `static.doubleclick.net`, `pubads.g.doubleclick.net`, `ad.doubleclick.net`, `fundingchoicesmessages.google.com`, `ogads-pa.clients6.google.com`, `imasdk.googleapis.com`, `mobile.events.data.microsoft.com`) under `BlockingMode.SMART` with zero user rules:

| Mode / Rule Source | Result | Observed Details |
| --- | --- | --- |
| Compiled `dns-rules.bin` (73,295 rules) + `never-block-hosts.txt` + `bundled_rules.txt` | **PASS (10/10 blocked)** | Verified by `AdHostsMustBlockTest.smartModeBlocksAllMustBlockHostsWithCompiledAndBundledRules` |
| Compiled `dns-rules.bin` + `never-block-hosts.txt` alone | **PASS (10/10 blocked)** | Verified by `AdHostsMustBlockTest.smartModeBlocksAllMustBlockHostsWithCompiledRuleSetAlone` |
| Embedded fallback seed list alone (`AdDetector.defaultSeedRules`, `context = null`, no `dns-rules.bin`) | **PARTIAL (7/10 by inspection)** | `AdDetector.defaultSeedRules` (`AdDetector.kt:118-185`) contains `doubleclick.net`, `googlesyndication.com`, and `imasdk.googleapis.com`, but omits `fundingchoicesmessages.google.com`, `ogads-pa.clients6.google.com`, and `mobile.events.data.microsoft.com`. Those 3 rely on `ad_domains.txt` (compiled into `dns-rules.bin`) and `bundled_rules.txt`. |

**Why earlier device exports could allow these hosts:** `DecideBlockingUseCase.kt` evaluates user allow rules (`Step 3`) before protected/static/extension rules (`Steps 6–7`), and `LearnViewModel.applyRules()` submits `ReviewAction.ALLOW` via `userRuleGateway.setWholeSite(...)` with substring heuristics pre-selecting `ALLOW` (`FIRST_PARTY` / `CDN_INFRA`). Any broad whole-site user allow (stored in HMAC `PrivateDomainSet`, uninspectable in UI) will override these hosts on a device. Addressed by WP-03 and WP-20.

---

## 2. Claims in `README.md`, `FEATURE_MATRIX.md`, and Docs Not Confirmed / Contradicted in Code

Per WP-00 rules, discrepancies below are recorded honestly without silently editing behavior or hiding gaps:

| Doc Location | Claimed Behavior | Observed Source / Test Reality | Related WP / Tracking |
| --- | --- | --- | --- |
| `README.md:76` | `"handles TCP queries on 10.10.0.1:53 and [fd00:1::1]:53"` | `TunnelDnsConfig.kt:12-27` configures `IPV4_CLIENT = 10.111.222.1`, `IPV4_DNS = 10.111.222.2`, `IPV6_CLIENT = fd00:1:fd00:1:fd00:1:fd00:1`, `IPV6_DNS = fd00:1:fd00:1:fd00:1:fd00:2`. (`10.10.0.1` / `fd00:1::1` are historical.) | Record here; fix doc copy later |
| `README.md:110` | `"APPROVED_ONLY mode (strict user allowlist)"` | `DecideBlockingUseCase.kt:133-137` Step 5 returns `ALLOW(NO_MATCH)` for domains absent from user allow/block sets. It is **not** a strict deny-all allowlist firewall. (`BEHAVIOR_CONTRACTS.md` and `FEATURE_MATRIX.md` F06 already clarify this; `README.md` still disagrees.) | `ADR-007` |
| `README.md:112` | `"Learn-an-App Mode: Zero-permission isolated per-app traffic recording session"` | `UnblockerVpnService.kt:242` only calls `builder.addDisallowedApplication(packageName)` (never `addAllowedApplication(targetPackage)`). All device DNS enters the VPN; `RecordingSessionManager.kt:114-116` drops UID mismatches but **accepts `uid == null`**, and `UnblockerVpnService.kt:287-294` passes `InetSocketAddress(clientAddr, 0)` and `InetSocketAddress(clientAddr, 53)` to TCP `uidResolver.resolveUid`. | **WP-02** |
| `README.md:164` & `PRIVACY.md:25` | `"unblocker_logs.db"` database filename | `UnblockerDatabase.kt:68` defines `DATABASE_NAME = "unblocker_v2.db"`. | Update in docs PR / Phase 2 |
| `README.md:206` | `"Target and compile SDK: Android 14 / API 35"` | API 35 is **Android 15** (`app/build.gradle.kts:50-56`: `compileSdk = 35`, `targetSdk = 35`). | Docs fix / **WP-10** |
| `README.md:126-127` | `"actively blocked"` claims across all named mobile ad networks & apps on-device | `BlockingRegressionCorpusTest` and `AdHostsMustBlockTest` verify synthetic rules in JVM; real app traffic, cold reboot recovery (`BootReceiver`), and `MY_PACKAGE_REPLACED` resume are **NOT VERIFIED on device**. | **WP-01**, on-device QA |
| `README.md:317-321, 329` | Claims `"cold-emulator reboot test"`, `"boot policy"`, and PR checks named `"build and instrumentation"` | `.github/workflows/android.yml` defines jobs `hygiene` and `build` (not `instrumentation`); `android-device-matrix.yml` is separate. Connected instrumentation has only 3 files (`LegacyUserRuleMigrationTest`, `LocalPrivacyAndVpnTest`, `VpnNetworkRegressionTest`). | Ongoing (`TEST_MATRIX`) |
| `docs/ARCHITECTURE.md:129` | `"Navigation is implemented with Jetpack Navigation Compose 2.8.3"` | `navigation-compose:2.8.3` is in `app/build.gradle.kts:207`, but `MainScreen.kt:97-156` uses manual `var selectedTab by remember { mutableStateOf(0) }` and `var isExtensionsOpen` overlay without `NavHost`. | **WP-11** |
| `docs/features/EXTENSIONS.md:14` | `"Current manifest v6 includes 19 records"` | `app/src/main/assets/extensions/extensions_manifest.json` is `version: 8` with **67** entries (`37` enabled by default). | Stale doc vs `FEATURE_MATRIX` F10 |
| `docs/FEATURE_MATRIX.md` (F01–F21) | Features listed as `"Source-observed"` without emulator run | Verified statically and in JVM tests only; hardware/emulator verification stays open until connected tests run. Additionally, F10 links to `FILTERING_RULES.md` while manifest details live across multiple files; F14 (`Per-app UID learning`) says "verify UID attribution on device" which exposes the `addAllowedApplication` gap. | **WP-02**, **WP-03** |
| `app/src/main/res/values/strings.xml` vs UI | Localization readiness | `strings.xml` contains 2 entries (`app_name`, `app_tagline`); ≥150 UI string literals are hardcoded directly in Compose / service / notification Kotlin files. `Theme.kt:73` exposes `dynamicColor` with no user preference wired. | **WP-12**, **WP-50**, **WP-53** |
| `app/src/main/java/com/unblocker/app/ui/learn/LearnViewModel.kt:337-391` | App-review categorization & safety | `categorizeDomain` uses substring `contains()` (`tracker`, `ads`, `packageSegments`); `ReviewAction.ALLOW` pre-selects whole-site `ALLOW` via `setWholeSite` with no warning on ad/shared domains. | **WP-03** |
| `BootReceiver.kt:26` | App update VPN restart | Handles `BOOT_COMPLETED` and `QUICKBOOT_POWERON`; lacks `MY_PACKAGE_REPLACED` so VPN stays off after app update. | **WP-01** |

---

## 3. Environment & Tooling Baseline

- **Gradle / Kotlin / SDK (`libs.versions.toml` + `app/build.gradle.kts`):** AGP `8.10.1`, Kotlin `2.0.21`, Compose BOM `2024.10.00`, Navigation Compose `2.8.3`, Hilt `2.51.1`, Room `2.6.1`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, `versionName = "2.7.2"`, `versionCode = 38`.
- **Android devices / emulators:** `adb devices` reported 0 attached devices; `emulator` binary not found in `$PATH` (local AVD metadata `Medium_Phone_API_37.0` exists on disk). Any `connectedDebugAndroidTest` item in subsequent WPs will be recorded honestly as `NOT RUN` unless an emulator/device becomes runnable.
