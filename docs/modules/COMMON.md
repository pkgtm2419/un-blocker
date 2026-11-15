# `:common` — shared models and dispatchers

**Source snapshot:** `main` `8934519` (2026-11-10); validate against checkout.

`common/src/main/java/com/unblocker/common/model/{DnsPacket,DomainRecord,RuleSource}.kt` define cross-module values; `dispatcher/CoroutineDispatchers.kt` and `DispatcherModule.kt` provide scheduling support. Other modules depend inward on common; do not introduce dependencies from common back to app/vpn-engine/data-store/ml-engine.

**Change procedure:** inspect callers in all four consumer modules, retain wire/serialization assumptions, write compatibility tests and review allocator/concurrency effects. A common DTO is not evidence that the app's active DNS parser uses it.

## Module rules

Keep dependencies consistent with `settings.gradle.kts`, use checked module build files, test existing contracts before changes, update `docs/PROJECT_MODULES.md`, source-controlled tests, feature matrix and generated symbol/module inventory. Record risk and verification in PR. [Module index](README.md) | [Architecture](../ARCHITECTURE.md).
