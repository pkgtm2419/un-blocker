# Privacy

unblocker performs filtering and learning on the Android device where it is
installed. It has no backend, account, analytics SDK, crash-upload service, remote
learning model or cloud synchronization. No learning data is sent to the developer.

## Data on the device

- Settings: private SharedPreferences, including filter switches, desired
  protection state, learning start date and the last local regression-check time.
- Learning: up to 20,000 HMAC-SHA256 domain identifiers, confidence scores and
  confirmation times in
  `noBackupFilesDir/learning-v1`. The per-installation HMAC key is non-exportable
  through the Android Keystore API. Hardware protection depends on the device.
- Local rules: up to 1,000 exact-domain allow identifiers and 1,000 exact-domain
  block identifiers in `noBackupFilesDir/allowlist-v1` and `blocklist-v1`, using
  the same non-exportable device key. The persisted sets cannot be reversed into
  domain lists by the app.
- Volatile data: DNS packet buffers, cached DNS responses and short cadence windows
  used for classification. These disappear with the process. Raw names necessarily
  exist in RAM while DNS is processed.

HMAC identifiers are pseudonymous, not anonymous against a compromised device or
an attacker who can use the app's key. The score store records no URLs, page
contents, accounts, per-query timestamps or complete query history. The two-week
threshold schedule is heuristic tuning, not training a cloud or large language model.

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
of selecting a third-party resolver. The configured resolver operator and network
can observe ordinary unencrypted UDP DNS. Website/app connections still use their
own network services. No learning, settings, local-rule or diagnostic payload is
attached to a DNS request. This app does not provide anonymity or encrypt traffic.

## Upgrade from 1.0.0

The old `learned_trackers.txt` contained raw learned domains. On first learning
initialization, the new version validates and converts those records to keyed
identifiers, writes a bounded atomic snapshot, then deletes the legacy file.
If Keystore/storage is unavailable, it uses ephemeral in-memory learning and
attempts to remove the old file. Basic static filtering remains available.
An OS/storage failure can prevent deletion; clearing app storage is the recovery.
Deletion is not a guarantee of forensic erasure on flash storage.

## Diagnostics

Connection states are local and do not log domains or exception messages. Local
regression checks use fixed blocked and legitimate sample domains with an isolated
in-memory learner; their passed/total result is not called real-world effectiveness
and never trains the user's persistent model. They run at most every six hours while
protection is desired; stopping protection cancels scheduled work. Android itself may retain system diagnostics
according to the device's settings. This policy describes the application code,
not the operating system or third-party DNS providers.
