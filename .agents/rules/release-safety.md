---
trigger: glob
globs: ".github/workflows/**,scripts/release-policy.py,scripts/verify-*.sh,app/build.gradle.kts"
---

# Release Safety

Before release-related edits read `docs/RELEASE_PROCESS.md`, `SECURITY.md`, root `CONTRIBUTING.md` and existing scripts. Verify version/tag alignment, real certificate/signing secret identity, license/source and APK artifact. Never publish/re-tag or silently overwrite public assets as part of documentation work. The new docs workflow proposes PRs only, never releases.
