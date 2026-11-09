# ROADMAP.md — Project Roadmap

**Milestone:** Un-Blocker v2.5.0: Essential Extensions, Tiered Filtering & UI Performance  
**Status:** In Progress  
**Spec Reference:** `.gsd/SPEC.md` (FINALIZED)

---

## Phase Overview

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 1** | Engine Optimization & State Synchronization Fix | In Progress |
| **Phase 2** | Curate the Essential 7 Extension Pack & Parser Pruning | Pending |
| **Phase 3** | Tiered Filtering (Basic/Optimal/Complete) & Mobile Unbreak | Pending |
| **Phase 4** | UI Performance, Page Navigation & Compose Optimization | Pending |
| **Phase 5** | Comprehensive Verification, APK Build & GitHub Release | Pending |

---

## Phase 1: Engine Optimization & State Synchronization Fix
- [ ] Task 1.1: Fix VPN toggle disconnect in `UnblockerScreen.kt`, `MainScreen.kt`, and `QuickStartManager.kt` so toggling protection never disables `adBlockingEnabled`.
- [ ] Task 1.2: Connect `OffHeapRuleEngine` to `AdDetector.kt` and `DecideBlockingUseCase.kt` for zero-heap binary rule evaluation.
- [ ] Task 1.3: Eliminate string slicing (`substring`) in `ExtensionRuleStore.kt` by using label offset walking on `CharSequence`.
- [ ] Task 1.4: Add `deofleadyetca.org` to quarantine list and update default seed rules.
- [ ] Task 1.5: Verify Phase 1 with regression unit tests.

## Phase 2: Curate the Essential 7 Extension Pack
- [ ] Task 2.1: Overhaul `extensions_manifest.json` from 53 entries down to the Essential 7 mobile-optimized lists.
- [ ] Task 2.2: Update `BoundedFilterDownloader.kt` and `RuleLineParser.kt` to filter out browser-only cosmetic rules (`##`, `###`, `#?#`) and keep pure DNS rules.
- [ ] Task 2.3: Verify Phase 2 with `ExtensionRepositoryTest`.

## Phase 3: Tiered Filtering & Mobile Unbreak Integration
- [ ] Task 3.1: Add `FilteringTier` (`BASIC`, `OPTIMAL`, `COMPLETE`) to `FilteringPreferences.kt`.
- [ ] Task 3.2: Implement `MobileUnbreakList.kt` protecting essential mobile apps (Supercell, Spotify, Play Store, WhatsApp CDN, banking).
- [ ] Task 3.3: Wire tier logic into `DecideBlockingUseCase.kt`: allow reward ads and shopping redirects under `BASIC`, balanced shield under `OPTIMAL`, strict shield under `COMPLETE`.
- [ ] Task 3.4: Verify Phase 3 with dedicated unit tests.

## Phase 4: UI Performance & Page Navigation Fixes
- [ ] Task 4.1: Debounce Room emissions in `LogViewModel.kt` (500ms throttle) and add paging/windowing (`take(100)`) in `LogViewerScreen.kt`.
- [ ] Task 4.2: Add Tier Selection Card (Basic / Optimal / Complete) to `UnblockerScreen.kt`.
- [ ] Task 4.3: Optimize `ExtensionsScreen.kt` for the Essential 7.
- [ ] Task 4.4: Verify UI performance and compose navigation without frame drops.

## Phase 5: Comprehensive Verification & GitHub Release
- [ ] Task 5.1: Run all unit tests (`./gradlew testDebugUnitTest`).
- [ ] Task 5.2: Build release test APK (`./gradlew assembleReleaseTest`).
- [ ] Task 5.3: Run `python tools/eval/release-policy.py preflight`.
- [ ] Task 5.4: Bump version to `2.5.0` (`versionCode 33`), tag `v2.5.0`, push to GitHub, and verify release artifacts.
