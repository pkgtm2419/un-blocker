# Changelog

All notable changes to Un-Blocker are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows the `versionName` / `versionCode` declared in `app/build.gradle.kts`.

> [!NOTE]
> This file was introduced by the agent-documentation pack (2026-11-14). Pre-2.5.0 history
> was **not** reconstructed from source; only information that could be verified against the
> checked-out implementation is recorded here. See [docs/REPO_RECONCILIATION.md](docs/REPO_RECONCILIATION.md)
> for known discrepancies between documentation and code.

---

## [2.7.0] — 2026-11-16 (versionCode 36)

### Added

- Expanded filter extensions based on uBlock Origin (`gorhill/uBlock`) and AdGuard (`filters.adtidy.org`): added AdGuard DNS Filter (191k+ rules via CDN Filter 15), AdGuard Mobile Ads (Filter 11), uBlock Origin Resource Abuse, Dan Pollock's Hosts File, and official regional language filters in manifest v8 (67 clean, deduplicated records).
- Pre-installed bundled seed rules expansion: added 54 mobile ad network, telemetry, and cryptomining rules in `bundled_rules.txt` for instant offline protection against CoinHive, Chartboost, Pangle, Fyber, Vungle, InMobi, AppsFlyer, Appnext, Samsung Ads, Ketchapp, and Yandex Mobile.
- Added comprehensive unit test coverage verifying that all bundled rules in `bundled_rules.txt` parse cleanly and block representative mobile ad, analytics, and cryptomining domains.
- Project-specific Antigravity agent instructions (`AGENTS.md`), scoped conditional rules and
  task-skill definitions under `.agents/`.
- Source-grounded reference documents: modules, features, runtime policy, security/privacy
  and testing (`docs/`).
- Documentation inventory generator (`scripts/docs_sync.py`), drift-check script
  (`scripts/docs_contract.py`) and proposed pull-request workflow (`docs/DOCUMENTATION_MAINTENANCE.md`).
- Regression coverage for missing inventories, malformed manifests, and link-handling edge cases.
- `AI_AGENT_GUIDE.md` consolidating agent quick-start, work-cycle and skill directory.
- `PROJECT_STATUS.md` with milestone history, CI/test coverage and known reconciliation items.

### Changed

- Reconciled documentation package against checkout `8934519` (2026-11-14); organised eight
  supplied historical references without applying proposed source patches.
- Scoped link checks to maintained documentation only.

### Fixed

- Release signing security: hardened `.github/workflows/release.yml` so production `v*` releases fail closed if production secrets or signer pins are missing, completely eliminating test-keystore and test-cert fallback (R14).
- Filter syntax safety: rejected request-scoped options (`$script`, `$image`, `$3p`, `$doc`) in `RuleLineParser` to avoid turning sub-resource filters into blanket DNS blocks (R12).
- Filter matching accuracy: separated `allowWildcard` set in `ExtensionRuleStore` so wildcard exception rules (`@@*.example.com`) only unblock strict subdomains without unblocking the apex host (R13).
- Hardened Android Lint gate in `app/build.gradle.kts` (`abortOnError = true`, `ignoreWarnings = false`) to enforce zero-error build quality (R07).
- Added startup pruning in `QueryLogSink` to enforce 24-hour raw event (max 1,000) and 30-day aggregate (max 10,000) retention limits (R15).
- Documented plaintext *My domains* storage, executable retention maintenance,
  conditional-rule and wildcard semantics, and release-signing fallback that were previously
  undocumented or inconsistently described.
- Made missing inventory files cause validation failure rather than silently pass.

---

## [2.6.1] — versionCode 35

> [!NOTE]
> ASSUMPTION: A specific changelog for v2.6.1 is not available in the repository materials.
> The entries below are inferred from the versionCode jump (33 → 35) and the fact that v2.5.0
> completed all five planned phases. Verify against the actual Git diff or release notes before
> publishing externally.

### Added

- ASSUMPTION: Incremental improvements or bug-fixes applied after v2.5.0 release that advanced
  `versionCode` from 33 to 35.

### Changed

- ASSUMPTION: Minor behaviour refinements or dependency updates consistent with a patch/minor
  release cycle following v2.5.0.

### Fixed

- ASSUMPTION: Stability or correctness fixes identified during post-release validation of v2.5.0.

---

## [2.5.0] — versionCode 33

Completed in five phases. All phases verified per `.gsd/ROADMAP.md`.

### Added

**Phase 1 — Engine optimisation**
- Integrated `OffHeapRuleEngine` to reduce JVM heap pressure during DNS decision evaluation.
- `ExtensionRuleStore` substring elimination: replaced linear substring scans with an
  efficient lookup structure.
- Quarantine-mode updates to safely handle rule-set transitions without exposing unfiltered
  traffic.

**Phase 2 — Extension overhaul**
- Replaced previous extension bundle (53 lists) with Essential 7 mobile-optimised filter lists.
- `BoundedFilterDownloader` improvements: bounded memory, retry limits and improved error
  reporting.

**Phase 3 — Filtering tiers and unbreak list**
- Introduced `FilteringTier` enum (`BASIC` / `OPTIMAL` / `COMPLETE`) to give users control
  over aggressiveness vs. compatibility.
- Added `MobileUnbreakList` protecting connectivity for key services: Supercell games, Spotify,
  Google Play Store, WhatsApp CDN and common banking domains.
- Wired `FilteringTier` and `MobileUnbreakList` into `DecideBlockingUseCase` as the
  authoritative decision point.

**Phase 4 — UI and performance**
- `LogViewModel` 300 ms throttle to prevent log-screen jank during high-traffic periods.
- Log paging/windowing capped at 150 entries (`take(150)`) to bound memory usage.
- Tier Selection Card UI: in-app control surface for selecting `FilteringTier`.
- `ExtensionsScreen` updated to surface and explain the Essential 7 optimised extension set.

### Changed

- VPN toggle disconnect fix: resolved a race where rapid toggle caused the VPN session to
  stall instead of cleanly reconnecting.

### Fixed

- Stability issues identified during Phase 5 verification prior to the v2.5.0 release build.

---

## Pre-2.5.0

> [!NOTE]
> Release history prior to v2.5.0 was not reconstructed from source. The inspected repository
> at `main` `8934519` contains stale version references in `README.md` and `.gsd` files
> (e.g. versionName 2.3.0 / versionCode 28). See [docs/REPO_RECONCILIATION.md](docs/REPO_RECONCILIATION.md)
> (items R01, R04–R08, R14–R15) for details of known discrepancies.

---

[Unreleased]: https://github.com/pkgtm2419/un-blocker/compare/v2.7.0...HEAD
[2.7.0]: https://github.com/pkgtm2419/un-blocker/compare/v2.6.1...v2.7.0
[2.6.1]: https://github.com/pkgtm2419/un-blocker/compare/v2.5.0...v2.6.1
[2.5.0]: https://github.com/pkgtm2419/un-blocker/releases/tag/v2.5.0
