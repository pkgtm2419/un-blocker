# Modules and ownership

| Module | Purpose | Depends on | Notable paths |
| --- | --- | --- | --- |
| `:app` | Android entrypoint, manifest-bound VPN service, Compose UI, live domain decisions and app-side private learning | `:common`, `:data-store`, `:vpn-engine`, `:ml-engine` | `app/src/main/java/com/unblocker/app/{services,logic,domain,data,ui}/` |
| `:common` | Shared packet and rule-source models; coroutine dispatcher abstractions | none of project's modules | `common/src/main/java/com/unblocker/common/{model,dispatcher}/` |
| `:vpn-engine` | Reusable TUN builder, DNS parser/forwarder and VPN state APIs | `:common`, `:data-store` | `vpn-engine/src/main/java/com/unblocker/vpn/{builder,parser,resolver,service}/` |
| `:data-store` | Room v2 database, DAOs/entities/migration; rule caches, repositories | `:common` | `data-store/src/main/java/com/unblocker/data/{db,cache,repository}/` |
| `:ml-engine` | Local deterministic domain feature extraction/scoring and WorkManager batch analysis components | `:common`, `:data-store` | `ml-engine/src/main/java/com/unblocker/ml/{analyzer,classifier,worker}/` |

### `:app` subareas

| Package | Responsibility | Source anchor |
| --- | --- | --- |
| `services` | VPN lifecycle, protected resolver IO, caching/health/quick-start, extension update, recording session, DNS-over-TCP | `UnblockerVpnService.kt`, `DnsUpstreamClient.kt`, `QuickStartManager.kt` |
| `logic/dns` | IP+DNS decoding, strict upstream response metadata, checksums, local TCP fallback/responder | `DnsPacket.kt`, `DnsResponseValidator.kt`, `TcpDnsResponder.kt` |
| `logic/rules` | UBR2 rules, list syntax parsing/admission, extension snapshots, quarantine | `RuleSet.kt`, `RuleLineParser.kt`, `ExtensionRuleStore.kt` |
| `logic/analysis` | local HMAC evidence and adaptive heuristics, protected domains/PSL, NeverBlock, MobileUnbreak | `DeviceLearning.kt`, `PrivateEvidenceStore.kt`, `NeverBlockPolicy.kt` |
| `domain` | central policy, typed decisions/reasons | `DecideBlockingUseCase.kt`, `BlockingDecision.kt` |
| `data` | preferences, extension repository, bounded HTTPS downloads, query log sink, user-rule gateway, CSV export | `FilteringPreferences.kt`, `ExtensionRepository.kt`, `QueryLogSink.kt` |
| `ui` | navigation, protection, logs, learn, extensions, settings, adaptive layouts/motion/theme | `MainScreen.kt`, `ui/screens/`, `ui/{logs,learn,extensions,settings}/` |
| `receivers` | boot event / startup intent | `BootReceiver.kt` |

## Cross-module change rules

- Trace call sites before moving a class between modules. Similar file names do not imply a single code path.
- Keep `common` Android-light and without app references; avoid cyclic dependencies.
- `data-store` migrations must preserve stored records, including legacy user rule compatibility; inspect schema exports and migration tests.
- Document interface changes in `docs/modules/<MODULE>.md`, [behavior contracts](BEHAVIOR_CONTRACTS.md), and tests.
- Treat `ml-engine` comments mentioning TFLite as non-authoritative: code uses `HeuristicDomainClassifier`; confirm runtime bindings.

[Per-module docs](modules/README.md) | [Generated index](generated/README.md).
