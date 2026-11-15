# Integrations and external boundaries

## Actual integrations

- **Android platform:** `VpnService`, `ConnectivityManager`, network resolver discovery, foreground notifications, boot receiver, Keystore, Storage Access Framework, WorkManager.
- **Local on-device libraries:** Jetpack Compose, Room, Hilt, coroutines, AndroidX Work and project Kotlin modules.
- **External DNS resolvers:** only allowed hostname queries are sent to resolvers chosen by Android's active underlying network in the manifest service path; they can see ordinary DNS. Review `vpn-engine` separately before stating complete absence of public defaults.
- **Rule source URLs:** optional user/catalog HTTPS list downloads (input data, never executable plugin code). All redirects/targets/content must pass policy checks.
- **GitHub:** source and build/release CI. The docs sync workflow can propose a PR if permitted; it must not push directly or publish APKs.

## Explicit non-integrations

No backend REST/GraphQL API, user account, payment service, remote machine-learning inference, telemetry collector, cloud history sync or arbitrary network control API in the inspected source. If a future feature introduces one, first update `PRODUCT.md`, privacy/security threat model, consent UI, module boundaries and test matrix.
