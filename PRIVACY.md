# Privacy

unblocker performs filtering and learning on the Android device where it is
installed. It has no backend, account, analytics SDK, crash-upload service, remote
learning model or cloud synchronization. No learning data is sent to the developer.

## Data on the device

- Settings: private SharedPreferences, including filter switches, desired
  protection state, learning start date and aggregate synthetic health-check results.
- Learning: up to 5,000 HMAC-SHA256 domain identifiers and confidence scores in
  `noBackupFilesDir/learning-v1`. The per-installation HMAC key is non-exportable
  through the Android Keystore API. Hardware protection depends on the device.
- Volatile data: DNS packet buffers, cached DNS responses and short cadence windows
  used for classification. These disappear with the process. Raw names necessarily
  exist in RAM while DNS is processed.

HMAC identifiers are pseudonymous, not anonymous against a compromised device or
an attacker who can use the app's key. The score store records no URLs, page
contents, accounts, per-query timestamps or complete query history. The seven-day
threshold schedule is heuristic tuning, not training a cloud or large language model.

App backup is disabled and explicit cloud/device-transfer exclusions cover private
files, preferences and databases. Learning is additionally kept in no-backup
storage. Older backups made before this update cannot be removed by the app.
Uninstalling/clearing app storage removes local data through Android. Stop
protection and choose **Clear local learning** to reset scores and the learning
start date without reinstalling. This does not erase another app's DNS cache.

## Network traffic

Blocked queries receive a local response. Allowed DNS requests go to `8.8.8.8`,
then `1.1.1.1`, `9.9.9.9` or `8.8.4.4` on failure. Resolver operators and the
network can observe ordinary unencrypted UDP DNS. Website/app connections still
use their own network services. No learning, settings or diagnostic payload is
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

Connection states are local and do not log domains or exception messages. Health
checks use synthetic sample domains and an isolated in-memory learner, never
training the user's persistent model. Android itself may retain system diagnostics
according to the device's settings. This policy describes the application code,
not the operating system or third-party DNS providers.
