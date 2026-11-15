# Module ownership directory

Each Gradle module has a focused implementation guide. Use these with [architecture](../ARCHITECTURE.md) and the actual module's `build.gradle.kts`; do not assume package naming proves runtime execution.

- [`:app`](APP.md) — active Android service and Compose product.
- [`:common`](COMMON.md) — shared models, dispatchers and contracts.
- [`:vpn-engine`](VPN_ENGINE.md) — reusable network component, not necessarily the manifest service implementation.
- [`:data-store`](DATA_STORE.md) — Room v2, migrations and rule caches.
- [`:ml-engine`](ML_ENGINE.md) — heuristic domain analysis and batch components.

Update the affected module doc whenever an API, dependency, runtime call graph, schema, manifest or user-facing responsibility changes. Regenerate machine ownership maps via `scripts/docs_sync.py`.
