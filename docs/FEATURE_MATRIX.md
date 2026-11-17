# Feature inventory and evidence matrix

**Last source snapshot:** 2026-11-17, versionName **2.7.1**

**Status meanings:** *Source-observed* = code/assets or existing tests identified; **not** equivalent to device testing, release acceptance or global efficacy. Update each row when semantics or implementation changes; create new rows for new features.

> [!IMPORTANT]
> **`APPROVED_ONLY` mode does NOT block unrecognized domains.** When a domain is not found in the user's allow or block lists (steps 3–4 of the decision chain), `APPROVED_ONLY` returns `ALLOW(NO_MATCH)` — it is **not** a full allowlist firewall/deny-all policy. See F06 and [docs/BEHAVIOR_CONTRACTS.md](BEHAVIOR_CONTRACTS.md) for the full decision chain.

| ID | Feature | Implementation evidence | Status / limitation | Last Verified |
| --- | --- | --- | --- | --- |
| F01 | Start/stop local DNS VPN; consent & notification | `QuickStartManager.kt`, `UnblockerVpnService.kt` | Source-observed; depends on Android consent/OEM | 2026-11-10 (src: 8934519) |
| F02 | IPv4/IPv6 DNS packet handling, malformed-query handling | `logic/dns/DnsPacket.kt`, `DnsResponseValidator.kt` | Source-observed; needs device regression | 2026-11-10 (src: 8934519) |
| F03 | Android-network DNS resolver forwarding, TCP retry, cache | `DnsUpstreamClient.kt`, `DnsResolverPolicy.kt`, `DnsCache.kt` | Source-observed; encrypted DNS may bypass | 2026-11-10 (src: 8934519) |
| F04 | Ad/tracker rules, seed datasets and UBR2 binary | `AdDetector.kt`, `logic/rules/RuleSet.kt`, `tools/filter-compiler` | Source-observed; bounded effectiveness | 2026-11-10 (src: 8934519) |
| F05 | Optional adult content filtering | `AdultContentDetector.kt`, `FilteringPreferences.kt` | Source-observed; DNS-only | 2026-11-10 (src: 8934519) |
| F06 | `SMART` and `APPROVED_ONLY` policy modes | `DecideBlockingUseCase.kt`, `FilteringPreferences.kt` | Source-observed; `APPROVED_ONLY` returns `ALLOW(NO_MATCH)` for domains absent from user allow/block lists (steps 3–4 in decision chain) — **not** a deny-all/allowlist firewall. See [BEHAVIOR_CONTRACTS.md](BEHAVIOR_CONTRACTS.md). | 2026-11-10 (src: 8934519) |
| F07 | Basic/Optimal/Complete tier selection | `FilteringPreferences.kt`, `FilteringTierCard.kt` | Source-observed; specific BASIC compatibility override | 2026-11-10 (src: 8934519) |
| F08 | Exact/whole-site private allow and block rules | `DeviceLearning.kt`, `PrivateDomainSet.kt`, `UserRuleGateway.kt` | Source-observed; allow has precedence over block | 2026-11-10 (src: 8934519) |
| F09 | Heuristic local learning/private evidence | `LocalNetworkLearner.kt`, `PrivateEvidenceStore.kt` | Source-observed; never label it cloud AI | 2026-11-10 (src: 8934519) |
| F10 | Preset/custom extension lists and toggles | `ExtensionRepository.kt`, `extensions_manifest.json` | Source-observed; **67 manifest entries** (manifest v8), see [FILTERING_RULES.md](FILTERING_RULES.md) | 2026-11-16 |
| F11 | Remote list HTTPS download/bounds/syntax admission | `BoundedFilterDownloader.kt`, `RuleLineParser.kt` | Source-observed; review SSRF/DNS rebinding | 2026-11-10 (src: 8934519) |
| F12 | Local aggregate query logs, optional raw event mode | `QueryLogSink.kt`, `DomainLogDao.kt` | Source-observed; local *plaintext* domains can be stored | 2026-11-10 (src: 8934519) |
| F13 | Log search, filters, clear/pause, CSV export & feedback | `LogViewModel.kt`, `CsvWriter.kt`, `DomainExporter.kt` | Source-observed; SAF export controlled by user | 2026-11-10 (src: 8934519) |
| F14 | Per-app UID learning / review-and-apply | `RecordingSessionManager.kt`, `LearnViewModel.kt` | Source-observed; verify UID attribution on device | 2026-11-10 (src: 8934519) |
| F15 | Boot restart intent, health and connectivity diagnostics | `BootReceiver.kt`, `HealthCheckService.kt`, `QuickStartManager.kt` | Source-observed; OEM/consent limit restart | 2026-11-10 (src: 8934519) |
| F16 | Private DNS coverage warning/recovery | `ProtectionUiStateMapper.kt`, `UnblockerVpnService.kt` | Source-observed; warns, cannot block all DoH | 2026-11-10 (src: 8934519) |
| F17 | Compose app, adaptive nav, settings and status UI | `ui/MainScreen.kt`, `ui/screens/`, `ui/components/` | Source-observed; accessibility/emulator verification needed | 2026-11-10 (src: 8934519) |
| F18 | Room v1→v2 migration and backup exclusions | `UnblockerMigrations.kt`, manifest backup XML | Source-observed; migration tests should verify upgrade | 2026-11-10 (src: 8934519) |
| F19 | Rule source provenance, compiler, release and CI gates | `DATASETS.md`, `sources.json`, `.github/workflows/` | Source-observed; release assets must be checked live | 2026-11-10 (src: 8934519) |
| F20 | My domains protected-site list | `data/preferences/MyDomainsStore.kt`, `DeviceUserRuleGateway.kt` | Source-observed; up to 500 plaintext names, distinct from HMAC sets | 2026-11-10 (src: 8934519) |
| F21 | ML/heuristic classifier (HeuristicDomainClassifier) | `ml-engine/src/main/java/com/unblocker/ml/` | Source-observed; TFLite 2.16.1 dep is present but deterministic heuristic is used, **NOT** a trained model — see R08 in [REPO_RECONCILIATION.md](REPO_RECONCILIATION.md) | 2026-11-10 (src: 8934519) |

## Change tracking contract

For each feature PR: change status and tests, add precise owner path, list user-visible acceptance and failure cases, update the corresponding `docs/features/*.md`, update `CHANGELOG.md` and root README if user-visible. New source features not yet documented are detected partly by generated inventory; **feature meaning cannot be inferred perfectly from source diffs**.

Detailed guides: [docs/features/README.md](features/README.md) | [Exact behavior](BEHAVIOR_CONTRACTS.md) | [Reconciliation issues](REPO_RECONCILIATION.md)

→ See [docs/BEHAVIOR_CONTRACTS.md](BEHAVIOR_CONTRACTS.md) for allow/block precedence, `APPROVED_ONLY` decision chain, protected-domain exceptions, and engine error behavior.
