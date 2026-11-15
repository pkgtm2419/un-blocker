---
trigger: glob
globs: "**/*.gradle.kts,gradle/**,app/src/main/AndroidManifest.xml"
---

# Gradle Dependencies

Read `docs/DEPENDENCIES.md`, `docs/CONFIGURATION.md`, `docs/BUILD_AND_TEST.md`. Preserve Gradle five-module edges, JDK 17 target, Android min SDK, version/catalog consistency and signing. Check release variant R8, dependency verification, licensing and nonbinary gates. Do not mute lint to claim success; check `docs/REPO_RECONCILIATION.md`. Update generated build facts and source version docs.
