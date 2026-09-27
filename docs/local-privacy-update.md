# Local privacy and reliability update

Confirmed scope: all filtering, learning, settings and diagnostics reside on the
installed device. Allowed DNS requests still use external resolvers; there is no
telemetry, learning upload, account or cloud synchronization.

Implementation checklist:
- [x] Private, bounded reputation store using Keystore HMAC-SHA256 identifiers;
      atomic snapshots in no-backup storage; migrate and delete legacy plaintext.
- [x] Truthful VPN lifecycle, persistent user intent, boot/permission handling,
      serialized shutdown and bounded forwarding with protected sockets.
- [x] Preserve learning on repeated queries, honor current filter switches,
      exclude all private data from backup and add local reset control.
- [x] Apache-2.0 license, honest privacy and build documentation, release version.
- [x] Regression tests, full JVM suite, lint, debug APK builds and emulator
      device tests. Report physical-device checks separately.

Tests exercise persistence/reload, identifiers differing by key, corrupt records,
legacy migration, capacity and concurrency, boot eligibility, failed establishment,
worker termination and settings transitions. A store failure must preserve basic
filtering while falling back to memory. No domain or exception message is logged.

Security limit: HMAC identifiers are pseudonymous and protected by a device key,
not a promise of anonymity on a compromised device. Deleting the legacy file
cannot guarantee forensic erasure on flash storage. Backups made by older app
versions cannot be retroactively removed.

Progress: implementation and planned verification are complete. Existing
untracked testing audit preserved. Work is on codex/local-private-learning.

Build environment: resolved Windows Unix-domain socket connection error using a
process-local JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\blocker and JDK 17.
Removed the repository's hardcoded developer-specific JDK path. Build and emulator
are run sequentially because simultaneous execution exhausted workstation memory.

Verified: 55 JVM tests pass with 0 failures, 0 errors and 0 skipped. Lint has
0 errors and 17 warnings (dependency/target updates, existing resources/icons
and obsolete API checks). Debug app/test APKs build; version is 1.1.0 (2), API
26 minimum/35 target, and legal/privacy assets are bundled.

Review corrections: scoped forwarding resources to each VPN run, serialized tunnel
establishment with teardown, added session-generation ownership, removed premature
STOPPED publication, made runtime Keystore errors nonfatal, and made reset remove
old snapshots even when the current store fell back to memory. The Keystore
failure regression was observed failing before the fix and passes afterward.

The final Android instrumentation run passed 5/5 tests. It exercised real VPN
establishment, routed an actual UDP DNS query through 10.10.0.1, verified the
blocked 0.0.0.0 response, and verified stop, restart and final shutdown states.
It also covered persisted boot intent and consent, denied consent, Keystore-backed
pseudonymous storage and deletion, and live settings re-evaluation.

A separate cold emulator reboot passed after the first device unlock: genuine
user-granted VPN consent survived, BOOT_COMPLETED launched the foreground service,
the tun0 interface returned at 10.10.0.2, and desired/running state remained true.
This is emulator evidence; an OEM physical-device reboot test remains a release
acceptance step because vendor background-start policies differ.

Final debug APK SHA-256:
236A45D23A5062CCD3AE2B08EF7B17F63F36655C2099921D18BCB0D170063740

Publication caveat: existing bundled blocklist provenance is undocumented. Source
code now has Apache-2.0 licensing, but third-party list rights must be verified
before redistribution. No release, push or signing-key change is performed.

Plan/audit cross-check: the repository is already Kotlin/Compose rather than the
Java/raw-SQL baseline assumed by the older migration plan, and no SQLite or Room
database API exists in app/src/main. The privacy implementation therefore uses a
small bounded atomic local store instead of adding a queryable browsing-history
database. Static checks found no hardcoded credentials, raw SQL, application
logging calls, or exported service/receiver. The launcher activity is the only
exported app component. Backup is disabled and private learning files are excluded.

Not claimed by this verification: 24-hour soak, low-storage/low-battery testing,
mobile-data and Wi-Fi handoff, OEM-specific background policy, measured battery/
CPU targets, or blocklist false-positive/provenance certification. Those items in
the broad testing audit require dedicated devices, elapsed time, and curated test
datasets and remain production-release acceptance work.
