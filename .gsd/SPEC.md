# SPEC.md — Project Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: No code may be written until this spec is marked `FINALIZED`.

## Vision
Deliver `v2.5.0` of `un-blocker`: Resolve UI lag and screen freezes across all pages, eliminate OOM crashes and GC thrashing by pruning 53 bloated filter extensions down to a curated Essential 7 Pack, integrate off-heap binary rule evaluation, implement a 3-tier filtering architecture (Basic, Optimal, Complete) with an active Mobile Unbreak list to prevent game and shopping breakage, fix the VPN state toggle disconnect that leaked DoubleClick and ad traffic, optimize Compose logging and page navigation, and publish signed release `v2.5.0` to GitHub.

## Goals
1. **Engine Optimization & State Synchronization Fix (Phase 1)** — Decouple `protectionEnabled` (tunnel state) from `adBlockingEnabled` (user preference) to prevent silent ad-blocking bypass; wire `OffHeapRuleEngine` directly into `AdDetector` and `DecideBlockingUseCase`; implement allocation-free label pointer walking on CharSequence; quarantine suspicious DGA domain `deofleadyetca.org`.
2. **Curated Essential 7 Extension Pack (Phase 2)** — Prune bloated 53-item `extensions_manifest.json` down to the Essential 7 mobile-optimized lists; update filter downloader and parser to strip cosmetic browser selectors (`##`, `###`, `#?#`) and preserve only pure DNS rules (`||domain^`, `0.0.0.0 domain`).
3. **Tiered Filtering & Mobile Unbreak Integration (Phase 3)** — Add `FilteringTier` (Basic, Optimal, Complete) to user preferences; implement `MobileUnbreakList` protecting essential app/game backends (Supercell, Spotify login, Google Play Store gateway, WhatsApp CDN); support reward ad preservation under Basic mode.
4. **UI Performance & Page Navigation Fixes (Phase 4)** — Debounce Room database emission flows (300ms throttle) and add paging/windowing to `LogViewerScreen` to prevent recomposition storms; add Tier Selection Card to `UnblockerScreen`; optimize `ExtensionsScreen` rendering and state.
5. **Comprehensive Verification & GitHub Release (Phase 5)** — Pass all unit tests across modules, build release test bundle, pass `release-policy.py preflight`, bump version to `2.5.0` (versionCode 33), tag `v2.5.0`, push to GitHub, and verify release artifacts.

## Constraints
- Android SDK 35 target, minimum SDK 26.
- Strict privacy: Zero PII or external telemetry.
- Zero JVM heap string allocations on DNS resolution hot path.
- 100% backward compatibility with existing Room databases and user rules.
- Single command execution per PowerShell invocation (no `&&` or `||`).
- Atomic commits per task (`type(scope): description`).

## Success Criteria
- [x] SPEC finalized and approved.
- [x] Phase 1: State disconnect fixed; off-heap rule evaluation verified; quarantine domain blocked.
- [x] Phase 2: Essential 7 manifest active; cosmetic rules stripped; unit tests pass.
- [x] Phase 3: Basic/Optimal/Complete tiers and Mobile Unbreak list verified with unit tests.
- [x] Phase 4: UI lag and recomposition storms resolved; tier selection card functional.
- [x] Phase 5: 900+ unit tests pass; `assembleReleaseTest` clean; tagged and released `v2.5.0` on GitHub.
