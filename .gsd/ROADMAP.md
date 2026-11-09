# ROADMAP.md — Project Roadmap

**Milestone:** Un-Blocker v2.5.0: Essential Extensions, Tiered Filtering & UI Performance  
**Status:** Completed  
**Spec Reference:** `.gsd/SPEC.md` (FINALIZED)

---

## Phase Overview

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 1** | Engine Optimization & State Synchronization Fix | Completed |
| **Phase 2** | Curate the Essential 7 Extension Pack & Parser Pruning | Completed |
| **Phase 3** | Tiered Filtering (Basic/Optimal/Complete) & Mobile Unbreak | Completed |
| **Phase 4** | UI Performance, Page Navigation & Compose Optimization | Completed |
| **Phase 5** | Comprehensive Verification, APK Build & GitHub Release | Completed |

---

## Phase 1: Engine Optimization & State Synchronization Fix
- [x] Task 1.1: Fix VPN toggle disconnect in `UnblockerScreen.kt`, `MainScreen.kt`, and `QuickStartManager.kt` so toggling protection never disables `adBlockingEnabled`.
- [x] Task 1.2: Connect `OffHeapRuleEngine` to `AdDetector.kt` and `DecideBlockingUseCase.kt` for zero-heap binary rule evaluation.
- [x] Task 1.3: Eliminate string slicing (`substring`) in `ExtensionRuleStore.kt` by using label offset walking on `CharSequence`.
- [x] Task 1.4: Add `deofleadyetca.org` to quarantine list and update default seed rules.
- [x] Task 1.5: Verify Phase 1 with regression unit tests.

## Phase 2: Curate the Essential 7 Extension Pack
- [x] Task 2.1: Overhaul `extensions_manifest.json` from 53 entries down to the Essential 7 mobile-optimized lists.
- [x] Task 2.2: Update `BoundedFilterDownloader.kt` and `RuleLineParser.kt` to filter out browser-only cosmetic rules (`##`, `###`, `#?#`) and keep pure DNS rules.
- [x] Task 2.3: Verify Phase 2 with `ExtensionRepositoryTest`.

## Phase 3: Tiered Filtering & Mobile Unbreak Integration
- [x] Task 3.1: Add `FilteringTier` (`BASIC`, `OPTIMAL`, `COMPLETE`) to `FilteringPreferences.kt`.
- [x] Task 3.2: Implement `MobileUnbreakList.kt` protecting essential mobile apps (Supercell, Spotify, Play Store, WhatsApp CDN, banking).
- [x] Task 3.3: Wire tier logic into `DecideBlockingUseCase.kt`: allow reward ads and shopping redirects under `BASIC`, balanced shield under `OPTIMAL`, strict shield under `COMPLETE`.
- [x] Task 3.4: Verify Phase 3 with dedicated unit tests.

## Phase 4: UI Performance & Page Navigation Fixes
- [x] Task 4.1: Debounce Room emissions in `LogViewModel.kt` (300ms throttle) and add paging/windowing (`take(150)`) in `LogViewerScreen.kt`.
- [x] Task 4.2: Add Tier Selection Card (Basic / Optimal / Complete) to `UnblockerScreen.kt`.
- [x] Task 4.3: Optimize `ExtensionsScreen.kt` for the Essential 7.
- [x] Task 4.4: Verify UI performance and compose navigation without frame drops.

## Phase 5: Comprehensive Verification & GitHub Release
- [x] Task 5.1: Run all unit tests (`./gradlew testDebugUnitTest`).
- [x] Task 5.2: Build release test APK (`./gradlew assembleReleaseTest`).
- [x] Task 5.3: Run `python scripts/release-policy.py preflight`.
- [x] Task 5.4: Bump version to `2.5.0` (`versionCode 33`), tag `v2.5.0`, push to GitHub, and verify release artifacts.
