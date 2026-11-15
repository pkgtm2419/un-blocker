# `app/` — Android entrypoint, actual VPN service, domain policy and UI

**Source snapshot:** `main` `8934519` (2026-11-10); validate against checkout.

`MainActivity.kt` and `UnblockerApplication.kt` create the UI, preferences, Hilt/WorkManager and app initialization. `app/src/main/AndroidManifest.xml` declares `UnblockerVpnService`, boot receiver and permission/backup boundaries. The app includes `services/`, `logic/dns/`, `logic/rules/`, `logic/analysis/`, `domain/usecase/`, `data/` and `ui/`.

**Hot path:** `UnblockerVpnService.kt` → `DnsPacketUtil` → `ContentFilterEngine` → `DecideBlockingUseCase`; blocked answers synthesized locally; allowed requests forwarded via `DnsUpstreamClient` after valid resolver selection. Data flow to `QueryLogSink` must be asynchronous.

**UI:** `MainScreen.kt` hosts protection, logs, extensions, learn app, settings; keep consent and service state synchronized. `FilteringPreferences` controls switches and mode/tier; user rules stored privately in `DeviceLearning`.

**Tests:** `app/src/test/`, `app/src/androidTest/`. Unit tests cover policy/dns/rules, instrumentation tests cover VPN and Keystore; actual device tests required before production claims.

## Module rules

Keep dependencies consistent with `settings.gradle.kts`, use checked module build files, test existing contracts before changes, update `docs/PROJECT_MODULES.md`, source-controlled tests, feature matrix and generated symbol/module inventory. Record risk and verification in PR. [Module index](README.md) | [Architecture](../ARCHITECTURE.md).
