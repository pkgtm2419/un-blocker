# Privacy

unblocker performs filtering and learning on the Android device where it is
installed. It has no backend, account, analytics SDK, crash-upload service, remote
learning model or cloud synchronization. No learning data is sent to the developer.

## Data on the device

- Settings: private SharedPreferences, including filter switches, desired
  protection state, learning start date and the last local regression-check time.
- Learning: up to 20,000 HMAC-SHA256 domain identifiers with bounded score,
  evidence state, mask, window count, user feedback and coarse day bucket in
  `noBackupFilesDir/learning-v3`. No raw names or per-query timestamps are saved.
  Its separate v3 HMAC key is non-exportable through Android Keystore; hardware
  protection depends on the device.
- Local rules: up to 1,000 exact-domain allow identifiers and 1,000 exact-domain
  block identifiers. Existing installations retain `allowlist-v1`/`blocklist-v1`
  and their compatible key. New installations use `allowlist-v3`/`blocklist-v3`
  and separate purpose keys. These identifiers do not contain readable names,
  but a party able to use the key could test guessed domains.
- Volatile data: DNS packet buffers, cached DNS responses and short cadence windows
  used for classification. These disappear with the process. Raw names necessarily
  exist in RAM while DNS is processed.
- Local domain logs: when enabled, query observations are aggregated in an on-device
  local database (`unblocker_logs.db`) providing one entry per domain (`domain_stats`:
  total counts, blocked/allowed counters, first/last seen) and bounded timestamp history
  (`domain_events`: up to 50 recent events per domain, automatic 7-day retention pruning).
  The database is local-only, excluded from backup and device transfer, and never
  uploaded or synced. Logging can be paused or cleared at any time from the in-app
  Logs tab. Domain names are never written to Logcat.

HMAC identifiers are pseudonymous, not anonymous against a compromised device or
an attacker who can use the app's key. The adaptive evidence store records no URLs,
page contents, accounts, per-query timestamps or complete query history. Optional
local query logging is bounded (capped per domain and pruned after 7 days) and strictly
confined to private on-device storage with user pause/clear controls. Independent
local evidence windows replace calendar thresholds; no cloud or large language model is trained.

App backup is disabled and explicit cloud/device-transfer exclusions cover private
files, preferences and databases. Learning is additionally kept in no-backup
storage. Older backups made before this update cannot be removed by the app.
Uninstalling/clearing app storage removes local data through Android. Stop
protection and choose **Clear local learning** to reset scores and the learning
start date without reinstalling. Use **Clear all** in Local domain rule to erase
allow and block rules. This does not erase another app's DNS cache.

## Network traffic

Blocked queries receive a local response. Allowed DNS requests go only to DNS
servers Android reports for a validated, non-VPN Wi-Fi/mobile network. The app has
no hard-coded public fallback. If none is available, DNS retries or fails instead
of selecting a third-party resolver. The VPN does not install broad host routes
for public resolver IPs, so it does not intentionally capture unrelated HTTPS
traffic to those addresses. A validated truncated UDP reply is retried over a
protected TCP connection to the same Android-configured resolver. The configured
resolver operator and network can observe ordinary unencrypted DNS. Website/app
connections still use their own network services. No learning, settings,
local-rule or diagnostic payload is attached to a DNS request. This app does not
provide anonymity or encrypt traffic.

The local negative response for `use-application-dns.net` is limited to Firefox's
automatic DoH compatibility mechanism. It does not disable Chrome DoH, Android
Private DNS, or manually configured application DoH, which can bypass this
DNS-only filter.

## Upgrade from legacy learning

Older `learned_trackers.txt` files could contain raw learned domains. On first v3
learning initialization, this version deletes that file and obsolete `learning-v1`
snapshots before opening the new store. Old learning evidence is not imported;
existing user allow/block rules keep their compatible files and key. If Keystore
is unavailable after cleanup, learning is memory-only. If legacy file deletion
fails, protection startup fails instead of reporting cleanup as complete;
clearing app storage is the recovery.
Deletion is not a guarantee of forensic erasure on flash storage.

## Diagnostics

Connection states are local and do not log domains or exception messages. Local
regression checks use fixed blocked and legitimate sample domains with an isolated
in-memory learner; their passed/total result is not called real-world effectiveness
and never trains the user's persistent model. They run at most every six hours while
protection is desired; stopping protection cancels scheduled work. Android itself may retain system diagnostics
according to the device's settings. This policy describes the application code,
not the operating system or third-party DNS providers.

## Evidence learning v3

Learning requires independent monotonic observation windows and corroborating
signal families, not elapsed installation days. Cadence and DNS response metadata
are bounded volatile memory. v3 persists keyed identifiers, score/state/mask,
saturating counters, feedback and coarse day buckets; no raw domains or per-query
times. Old reputation is reset under a separate v3 Keystore namespace. Existing
user rules retain their compatible old key; new installs separate allow/block
keys. User allow suppresses learned evidence. Removing a rule removes only its
feedback authority. Reset Learning removes both old and v3 snapshots without
removing user rules; Clear Rules removes feedback while retaining ordinary evidence.

