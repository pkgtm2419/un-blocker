# Un-Blocker

[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.2.2)-brightgreen?style=flat&logo=android&logoColor=white)](https://github.com/pkgtm2419/un-blocker/releases/download/test-v1.2.2/ub-blocker-1.2.2.apk)
[![Release CI](https://github.com/pkgtm2419/un-blocker/actions/workflows/release.yml/badge.svg)](https://github.com/pkgtm2419/un-blocker/actions/workflows/release.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Development 1.3.0](https://img.shields.io/badge/development-1.3.0-orange.svg)](app/build.gradle)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Privacy: local only](https://img.shields.io/badge/Privacy-local--only-success)](#privacy-by-design)

Un-Blocker is a free, open-source Android DNS blocker built around a simple
promise: filtering and learning belong on your device, not in somebody else's
analytics platform.

It creates a local Android VPN interface, evaluates DNS requests on the device,
and blocks recognized advertising, tracking, and optionally adult-content
domains. It does not proxy general web traffic, inspect encrypted page contents,
upload browsing history, or require an account.

> [!IMPORTANT]
> Un-Blocker is a DNS filter, not an anonymity VPN. Allowed DNS requests are sent
> to the DNS resolvers configured by Android for the active network. Those
> resolvers can see the requested domain and the device's network address.

## Download the APK

Implementation progress and verification: [phase tracker](docs/improvement-progress.md),
[measured matcher results](docs/performance.md), [DNS coverage and limitations](docs/dns-scope.md).

<div align="center">

[![Download APK](https://img.shields.io/badge/Download_APK-ub--blocker--1.2.2.apk-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://github.com/pkgtm2419/un-blocker/releases/download/test-v1.2.2/ub-blocker-1.2.2.apk)
[![Releases](https://img.shields.io/badge/All_Releases-GitHub-238636?style=for-the-badge&logo=github&logoColor=white)](https://github.com/pkgtm2419/un-blocker/releases)

</div>

### Latest Release Package

The links below are the published **1.2.2 test build**, not the new development
version. Source on this branch is **1.3.0**; it has not been published. Publishing
requires the stable signing secrets and a matching `test-v1.3.0` or `v1.3.0` tag.

| Asset | Link | Details |
| :--- | :--- | :--- |
| **Android APK** | [**`ub-blocker-1.2.2.apk`**](https://github.com/pkgtm2419/un-blocker/releases/download/test-v1.2.2/ub-blocker-1.2.2.apk) | Direct APK Download (Ready to install) |
| **Release Page** | [**Release `test-v1.2.2` Notes**](https://github.com/pkgtm2419/un-blocker/releases/tag/test-v1.2.2) | View release details and changelog |
| **All Releases** | [**GitHub Releases Catalog**](https://github.com/pkgtm2419/un-blocker/releases) | Browse all published version releases |

> [!TIP]
> Click the green **Download APK** button above to download the file directly to your phone. If you have an earlier build installed with an older test key, uninstall it once before installing this version.

## Why Un-Blocker?

- **Local-first privacy:** no accounts, telemetry, analytics, subscriptions,
  remote model calls, or cloud synchronization.
- **On-device adaptive learning:** improves local decisions using domain shape,
  structural signals, and short-lived query cadence observations.
- **Private persistence:** learned domains and custom rules are stored as
  device-keyed HMAC-SHA256 identifiers rather than readable domain names.
- **User control:** exact-domain allow and block rules apply immediately.
- **Transparent behavior:** source code, filtering limitations, datasets, tests,
  and privacy boundaries are documented publicly.
- **No hidden resolver:** allowed queries use only Android-configured resolvers;
  there is no hardcoded Google, Cloudflare, Quad9, or other public fallback. If
  resolver discovery is temporarily empty, the request fails closed and the
  client can retry after Android publishes current network resolvers.
- **Resilient VPN lifecycle:** protection intent survives ordinary process death
  and can restart after boot when consent and Android policy allow it.
- **Open source:** application code and project-maintained seed data are released
  under the Apache License 2.0.

## Features

### DNS filtering

- Local DNS interception through Android `VpnService`.
- Advertising and tracker-domain detection.
- Optional adult-content domain filtering.
- Deterministic local seed lists with documented provenance.
- Strict domain, DNS query, and upstream-response validation.
- Standards-correct IPv6 UDP checksums for local and forwarded responses.
- Protected DNS-over-TCP retry when an upstream UDP response is truncated.
- TTL-aware DNS response cache with a defensive ten-minute maximum.
- Android network resolver discovery with VPN, loopback, unspecified, and
  duplicate addresses excluded.

### Private self-learning

- Evidence-based local learning: independent observation windows and corroborating signals; installation age never authorizes blocking.
- Capacity for up to 20,000 learned reputation entries.
- Weak evidence expires after seven days; confirmed evidence expires after 30 days without renewed confirmation.
- Android Keystore-backed HMAC-SHA256 identifiers.
- Atomic snapshots stored in Android's private no-backup directory.
- New v3 learning namespace resets old evidence without re-keying opaque IDs; existing user rules retain compatible keys.
- No raw DNS history, URL history, page content, or reconstructed domain list.

The learning system is intentionally heuristic. False positives and missed
trackers remain possible, and this project does not claim an invented blocking,
latency, battery, or accuracy percentage.

### Local rules and controls

- Up to 1,000 private exact-domain allow rules.
- Up to 1,000 private exact-domain block rules.
- Allow rules take precedence if a domain is present in both sets.
- Live filter-switch and rule re-evaluation.
- One-tap reset for locally learned reputations.
- Clear-all controls for private domain rules.
- Six-hour local regression check while protection is enabled.

Because custom rules are stored as one-way identifiers, the app cannot display a
saved plaintext list. Enter the same domain again to remove it, or use the
appropriate **Clear all** action.

## How it works

```text
Android application
        |
        v
Local VpnService (DNS traffic only)
        |
        +--> Validate DNS packet and domain
        |
        +--> Local allow rule? ----------> allow
        |
        +--> Local block/seed/heuristic? -> return local blocked response
        |
        +--> Allowed query --------------> Android-configured network DNS
                                             |
                                             +--> Truncated UDP? protected TCP retry
                                             |
                                             v
                                      TTL-aware memory cache
```

All classification, custom rules, learning, settings, health checks, and cache
management run inside the installed application. Only an allowed DNS request
leaves the device, because an external resolver is required to resolve it.

## Privacy by design

Un-Blocker does **not** include:

- analytics or advertising SDKs;
- user accounts or device-to-account identifiers;
- domain, URL, browsing-history, or exception logging;
- cloud or federated learning;
- uploaded training data or remote models;
- automatic blocklist downloads;
- cross-app behavior tracking;
- TLS interception, custom certificates, or page-content inspection;
- plaintext persistence of learned or user-entered domains;
- private-data backups.

Raw domain names exist transiently in memory while a DNS request is parsed,
evaluated, and—if allowed—resolved. HMAC identifiers are pseudonymous and protect
data at rest, but they are not a promise of anonymity on a compromised device.

Read the complete [privacy policy](PRIVACY.md) before installing or contributing.

## Filtering boundaries

Un-Blocker can block a hostname only when its DNS request passes through the
local VPN. Filtering may be bypassed or limited by:

- browser or application DNS-over-HTTPS;
- Android Private DNS behavior;
- hardcoded server IP addresses;
- cached DNS results created before protection started;
- ads served from the same domain as wanted content;
- encrypted or non-DNS traffic outside the local DNS interception path;
- manufacturer background-start and battery policies.

The project deliberately avoids TLS interception and certificate installation.
Those techniques would expand access to sensitive traffic and conflict with this
project's privacy model.

For Firefox's automatic DoH compatibility mechanism, Un-Blocker returns a local
negative response for `use-application-dns.net`. This does not disable Chrome
DoH, Android Private DNS, or DoH that a user deliberately configured in an app;
those encrypted paths can bypass a DNS-only VPN.

## Requirements

- Current source version: 1.3.0 (`versionCode` 6); published test APK: 1.2.2.
- Android package: `com.unblocker.app`.
- Android 8.0 or newer (API 26+).
- Target and compile SDK: Android 16 / API 36.
- Android VPN consent.
- A network exposing at least one usable Android-configured DNS resolver.
- JDK 17 and Android SDK 36 when building from source.

## Install and use

1. Download the APK from the [Download section](#download-the-apk) or the
   [official Releases page](https://github.com/pkgtm2419/un-blocker/releases).
2. Allow installation from the selected source if Android asks.
3. Open **Un-Blocker** and tap the power button.
4. Review and accept Android's VPN consent dialog.
5. Review the adult-content filter and disable it if you do not want it.
6. Use **Local domain rule** to allow or block an exact domain.
7. Open **Always-on VPN settings** if you want Android-managed startup.

Leave **Block connections without VPN** disabled. Un-Blocker is a DNS-only split
tunnel and does not route arbitrary internet traffic through a remote VPN server.

The status can be `STARTING`, `RUNNING`, `STOPPED`, or `ERROR`. `RUNNING` confirms
that Android established the local TUN interface; it does not guarantee that the
current upstream DNS resolver is reachable.

Boot startup requires all of the following:

- protection was enabled by the user before reboot;
- automatic restart is enabled;
- Android VPN consent is still valid;
- the user has unlocked the device after boot;
- Android and the device manufacturer permit the background start.

Force-stop, revoked VPN consent, battery restrictions, and manufacturer-specific
auto-start policies can prevent automatic restart.

## Permissions

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Forward allowed DNS requests to the network's configured resolver. |
| `ACCESS_NETWORK_STATE` | Observe validated non-VPN networks and their DNS configuration. |
| `CHANGE_NETWORK_STATE` | Support Android VPN network setup. |
| `BIND_VPN_SERVICE` | Restrict the VPN service to Android's VPN framework. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep user-enabled DNS protection active with a visible notification. |
| `RECEIVE_BOOT_COMPLETED` | Restore previously enabled protection after reboot when Android permits it. |
| `POST_NOTIFICATIONS` | Display foreground protection status on supported Android versions. |

The application disables Android backup for its private data. App-defined
services and receivers are not exported to other applications.

## Build from source

Clone the public repository:

```bash
git clone https://github.com/pkgtm2419/un-blocker.git
cd un-blocker
```

Install JDK 17, Python 3.11 (CI version) and Android SDK platform 36. Set `JAVA_HOME` and `ANDROID_HOME`, or
create an untracked `local.properties` containing `sdk.dir`.

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
python -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
```

Linux or macOS:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
python -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
```

The connected suite requires a disposable emulator or test device. It changes
the test installation's VPN app-op and local application data.

Build output:

```text
app/build/outputs/apk/debug/ub-blocker-1.3.0.apk
```

Gradle derives this filename from `versionName`, so future builds automatically
use `ub-blocker-{version}.apk` after the project version changes.

This APK is signed with a development key. A production update must use the same
production signing key as the previously installed production release. Never
commit signing keys, credentials, `local.properties`, or captured traffic.

On Windows systems affected by Java's Unix-domain socket error inside a managed
desktop runner, point Java at a short path that does not exist for that build
shell only. This makes Java use its TCP selector fallback:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/codex-java-no-uds'
```

Do not create that directory. Normal terminals that do not reproduce this Java
error do not need the workaround.

## Testing and verified scope

The current project suite includes:

- JVM tests covering filtering, learning, DNS parsing, checksums, strict response
  validation, UDP-to-TCP fallback, caching, concurrency, lifecycle state,
  resolver policy, and dataset provenance;
- Android instrumentation tests covering Android Keystore persistence, private
  rules, settings, boot policy, denied consent, and a real VPN-routed DNS block;
- Android lint, debug APK assembly, test APK assembly, manifest checks, and APK
  signature verification;
- a separate cold-emulator reboot test for automatic VPN restoration.

See [the local privacy and reliability update](docs/local-privacy-update.md) for
the exact verification record and remaining production acceptance work.

The [Phase 5 quality record](docs/phase5-verification.md) covers the small labeled
corpus, seeded malformed packets and resolver transitions. CI exports confusion
metrics and the compiler's rule count/SHA-256 manifest. Stable check names are
`build` and `instrumentation`; both run for every pull request to `main`.

APK binaries are no longer tracked in the source tree (history is preserved).
Release CI fails if stable signing secrets, an exact version tag or the expected
certificate are missing. Existing release assets are never silently overwritten.

Not yet claimed by the project: a 24-hour physical-device soak, every OEM's
background policy, independently certified false-positive rates, production
signing readiness, or independently measured battery and CPU targets.

## Project structure

```text
app/src/main/java/com/unblocker/app/
├── data/          Local settings and models
├── domain/        Blocking decisions and use cases
├── logic/         Detection, validation, DNS, and private learning
├── receivers/     Reboot restoration
├── services/      VPN lifecycle, forwarding, and local health checks
└── ui/            Jetpack Compose interface

app/src/test/       JVM regression tests
app/src/androidTest Android device/emulator integration tests
```

Technology: Kotlin, Jetpack Compose, coroutines, Android `VpnService`, Android
Keystore, `SharedPreferences` for small non-sensitive settings, and bounded
application-private files for pseudonymous learning and rules.

## Open-source data and licensing

Application code is licensed under the [Apache License 2.0](LICENSE).
Project-maintained seed lists are also Apache-2.0 data and carry SPDX headers.
No undocumented third-party bulk list is shipped.

Review:

- [Dataset provenance](DATASETS.md)
- [Privacy policy](PRIVACY.md)
- [Contribution guide](CONTRIBUTING.md)
- [Project notice](NOTICE)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Contribute to a fully private blocker

Un-Blocker is intended to become a community-maintained, privacy-first Android
blocker. Contributions from Android developers, privacy researchers, DNS experts,
testers, designers, technical writers, and accessibility specialists are welcome.

Useful contribution areas include:

- expand deterministic, properly licensed filtering regression datasets;
- improve DNS parser correctness and malformed-packet coverage;
- test reboot and network transitions across Android manufacturers;
- reduce battery, memory, and CPU use with reproducible benchmarks;
- improve accessibility, translations, onboarding, and status explanations;
- strengthen private local learning without uploading user data;
- document bypass cases and privacy boundaries clearly;
- reproduce and fix false positives with synthetic or consented test data.

Before opening a pull request:

1. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [PRIVACY.md](PRIVACY.md).
2. Keep filtering and learning on the installed device.
3. Do not add telemetry, remote models, uploaded domains, traffic logs, automatic
   accounts, or hidden resolver fallbacks.
4. Add focused regression tests for changed behavior.
5. Run JVM tests, lint, and APK assembly; run instrumentation tests for Android,
   VPN, preferences, or Keystore changes.
6. Document the license and provenance of every new dataset or dependency.

[**Browse open issues**](https://github.com/pkgtm2419/un-blocker/issues) ·
[**Start a contribution**](https://github.com/pkgtm2419/un-blocker/fork) ·
[**Review pull requests**](https://github.com/pkgtm2419/un-blocker/pulls)

Help build an Android blocker whose users do not have to trade their browsing
privacy for protection.

## Disclaimer

Un-Blocker is provided without warranties under the Apache License 2.0. Domain
filtering cannot guarantee complete blocking, anonymity, parental-control
enforcement, or protection against malicious software. Review the source,
privacy policy, and release provenance before installing.
