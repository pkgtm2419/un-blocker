# Privacy data flow and retention

**Do not conflate private hashed learning with local plaintext aggregate logs.** Source read: `PRIVACY.md`, `DeviceLearning.kt`, `PrivateEvidenceStore.kt`, `PrivateDomainSet.kt`, `QueryLogSink.kt`, `data-store/`, Android backup configuration.

| Data class | Where | At-rest format | User control / boundary |
| --- | --- | --- | --- |
| Device learning | `noBackupFilesDir/learning-v3` | Keystore-keyed HMAC-SHA256 identifiers and coarse evidence | reset; no uploaded history |
| User allow/block rules | private per-app files, legacy v1 or v3 | HMAC identifiers; compatible migrations | add/remove/clear; cannot enumerate original plaintext domains |
| My domains protection list | SharedPreferences `my_domains_store`, key `my_domains_list` | **plaintext normalized domains**, up to 500 | protect/remove/import/clear; separate from HMAC allow/block sets |
| Settings | SharedPreferences | settings values, no raw domain browsing history | settings toggles/clear |
| Query aggregates | Room `unblocker_logs.db`, `domain_stats` | **plaintext domain name** plus counts and first/last seen | local only; pause/clear; bounded retention per implementation |
| Raw per-query domain events | Room `domain_events` when `RAW_HISTORY_OPT_IN` | **plaintext domain** plus timestamps/decision | opt-in event mode; default `AGGREGATE_ONLY` at `QueryLogSink` startup; short retention |
| In-memory DNS/cache/cadence | process memory | raw domains and DNS packets transiently | process termination, cache eviction |
| Exported CSV | user-selected file via Android SAF | user-readable domain stats | export is explicit user action; prevent spreadsheet formula injection |

## Current implementation facts that must be reconciled

- `QueryLogSink` defaults to `HistoryMode.AGGREGATE_ONLY`, while `FilteringPreferences` defaults its query-log **pause flag** to false; aggregates may be stored without an opt-in raw history setting. Do not say "no DNS names stored anywhere".
- `QueryLogSink.pruneOldLogs()` calls DAO deletion with a 24-hour cutoff / 1,000 global event cap and a 30-day cutoff / 10,000 global aggregate cap. The flush loop invokes pruning approximately hourly while running, rather than after each insert or at startup. These are maintenance targets; entries can exceed them between runs or while the process is stopped. The DAO's 50-event read limit is a display limit, not a per-domain storage cap. `PRIVACY.md` still describes 7-day retention; see [reconciliation](REPO_RECONCILIATION.md).
- `RAW_HISTORY_OPT_IN` exists as a sink API, but no production caller of `setHistoryMode` was found in this checkout. Do not describe a working user-facing raw-history opt-in toggle without verifying its wiring.
- `MyDomainsStore` persists readable protected-domain names. `DeviceUserRuleGateway.protectDomain()` uses that store, while `set()` and `setWholeSite()` use HMAC-backed `DeviceLearning` sets. The privacy boundary depends on the specific user action.
- `UnblockerDatabase` has v2 entities for both legacy DNS logs and new domain stats; confirm actual write paths before writing product descriptions. Some `:ml-engine` worker paths may differ from `app` live logging.
- `AndroidManifest.xml` uses `allowBackup=false`; backup and transfer exclusion XMLs are present. Validate exclusions on supported Android versions, especially upgrades.
- Hashes are **pseudonyms**, not anonymity under a compromised device with key access. Allowed DNS queries still go to a network resolver.

## Engineering rules

No browsing-domain Logcat; no network upload of logs/learning, new remote SDK or silent export; no unbounded queue/storage. Clear operations must honor generation/barrier semantics so old queue items cannot repopulate history. Test user-facing disclosure and clear behavior whenever query logging changes.

See [Security review](SECURITY_REVIEW.md), [Privacy policy](../PRIVACY.md), [Initial audit](audits/INITIAL_REVIEW_2026-11-13.md).
