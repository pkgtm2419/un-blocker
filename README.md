# unblocker

Open-source Android DNS blocker with on-device filtering and adaptive tracker
learning. Kotlin, Jetpack Compose, Android 8.0+ (API 26), target API 35.

## Privacy and learning

All blocking decisions, learning and settings stay on the installed device.
There are no accounts, analytics, telemetry, remote model calls, subscriptions or
automatic blocklist downloads. The app does not collect URLs or page contents.

Learning uses lexical, structural and short-lived query cadence signals, with a
confidence threshold that changes over seven days. It is a heuristic system;
false positives and missed trackers are possible. There is no guaranteed blocking
percentage or latency. Learned reputations survive restarts as at most 5,000
device-keyed HMAC-SHA256 identifiers and scores in private no-backup storage.
The key lives in Android Keystore. Raw domain names are held temporarily in memory
for filtering and DNS resolution; they are not written to the new learning file.

Allowed queries are forwarded over ordinary UDP DNS to Google Public DNS and,
on failure, Cloudflare/Quad9/Google secondary. These resolvers can see the queried
domain and network address. DNS transport is not encrypted. This app is a local
DNS filter, not an anonymity VPN or an offline DNS resolver. Private DNS, browser
DoH, hardcoded IPs and shared content/ad domains can bypass or limit filtering.
See [PRIVACY.md](PRIVACY.md) for data handling and migration details.

## Use

1. Build and install the debug APK below, open unblocker, and tap the power button.
2. Accept Android's VPN consent. Enable adult filtering if wanted.
3. For system-managed restarts, use **Always-on VPN settings** in the app.
   Leave **Block connections without VPN** off: this is a DNS-only split tunnel.
4. To erase learned reputations, stop protection and tap **Clear local learning**.

The app reports STARTING, RUNNING, STOPPED or ERROR. RUNNING means the TUN
interface was established, not that an upstream resolver is currently reachable.
Boot startup after the first unlock requires previous user-enabled protection,
auto-restart enabled and current VPN consent. Explicit stop/revocation clears that
intent. Always-on VPN is separately controlled by Android; turn it off in Android
settings if you want protection to stay stopped. Android battery restrictions,
force-stop and manufacturer auto-start policies can prevent automatic startup.

## Build and test

Install JDK 17 and Android SDK platform/build tools 35. Set JAVA_HOME and
ANDROID_HOME (or create an untracked local.properties with sdk.dir).
No developer-specific JDK path is checked into the build.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

The second command requires a disposable emulator/device and exercises local
Keystore storage, filter switches, boot policy, denied consent and real DNS
blocking. Tests change the test installation's VPN app-op and learning data.
Use ./gradlew on Linux/macOS. Build dependencies require internet access;
runtime learning does not.

Output: app/build/outputs/apk/debug/app-debug.apk (version 1.1.0). This uses a
development signing key. A production update must use the same signing key as the
installed release. The tested build output above is authoritative; APK files under
release/ are not published by Gradle and must not be treated as production builds.
No updated release is published automatically.

On Windows systems affected by Java's Unix-domain socket temporary-path error,
set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\blocker for the build shell
only (substitute a short, existing, writable local directory).

## Open source

Application code is licensed under [Apache License 2.0](LICENSE).
See [NOTICE](NOTICE), [CONTRIBUTING.md](CONTRIBUTING.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Existing bundled seed-list
provenance is documented separately; the application license does not override
third-party rights. Contributions should include regression tests and preserve
the local-only learning model.
