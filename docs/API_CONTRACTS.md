# Public interfaces and internal contracts

This is an **Android app without an HTTP backend/API**. Do not create REST or MCP endpoints or document fictional service routes. The important APIs here are in-process Kotlin contracts and Android OS interfaces.

| Interface | Contract | Owner |
| --- | --- | --- |
| `MainActivity` → `MainScreen` | displays app and requests Android VPN consent as required | `app/ui`, app root |
| `QuickStartManager.startBlockingServices` / `stopBlockingServices` | user intent, permission handling, worker scheduling | `app/src/main/java/com/unblocker/app/services/QuickStartManager.kt` |
| `UnblockerVpnService.start/stop`, `session` | platform-visible lifecycle, status and coverage | `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt` |
| `ContentFilterEngine.analyzeAndFilter` | per-domain evaluation | `app/src/main/java/com/unblocker/app/logic/ContentFilterEngine.kt` |
| `DecideBlockingUseCase.invoke` | typed allow/block decision & reason | `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt` |
| `DnsPacketUtil`, `DnsResponseValidator` | parse and construct bounded DNS messages | `app/src/main/java/com/unblocker/app/logic/dns/` |
| `DnsUpstreamClient.resolve` | protected forwarding and validation | `app/src/main/java/com/unblocker/app/services/DnsUpstreamClient.kt` |
| `FilteringPreferences` flows | filtering toggles, modes, tier, pause, state | `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt` |
| `UserRuleGateway` | `set`/`setWholeSite`: HMAC rule updates; `protectDomain`: plaintext My domains preferences; `reset`: clears matching entries | `app/src/main/java/com/unblocker/app/data/repository/` |
| `DomainLogDao` / `QueryLogSink` | bounded async local domain aggregates | `data-store/src/main/java/com/unblocker/data/db/dao`, `app/src/main/java/com/unblocker/app/data/logs` |
| `ExtensionRepository` | active list state/download/update lifecycle | `app/src/main/java/com/unblocker/app/data/repository/ExtensionRepository.kt` |

For changes record method signature, threading context, input validation, result/error types, persisted side effects, compatibility and tests. Avoid inventing external network APIs for data that never leaves the phone.
