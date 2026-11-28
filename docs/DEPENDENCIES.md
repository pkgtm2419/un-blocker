# Dependency architecture and update policy

`gradle/libs.versions.toml` is the current version catalog. Snapshot: AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21 (AGP built-in), KSP 2.3.14, Compose BOM 2025.12.01, Hilt 2.60.1, Room 2.8.5, AndroidX WorkManager 2.9.1 and Kotlin coroutines 1.11.0 (revalidate in checkout). JDK 17 and compile SDK 35 are declared in Gradle. Avoid duplicating versions in separate build scripts.

## Updating dependencies

1. Identify module consumers and required minimum SDK/AGP/Kotlin compatibility; review the upstream change log and security advisories.
2. Do not add external data-collection SDKs, AI cloud clients, untrusted remote update binaries, or unnecessary native libraries.
3. Update catalog and dependency graph; run Gradle dependency verification (see `gradle/verification-metadata.xml`) and existing nonbinary check.
4. Run affected JVM/instrumentation tests, lint and APK build; evaluate app-size and battery/CPU effects only with measured data.
5. Update this document, build facts (`docs_sync --write`), license/notice records and `CHANGELOG.md` as warranted.

Dependency inventory is **not** a runtime SBOM. For reproducible security review prefer resolving Gradle's dependency graph in CI and recording versions, hashes and licenses.
