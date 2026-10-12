# Plan 06: Release Readiness and Documentation

## Objective

Establish unambiguous version consistency, artifact provenance, and verifiable release automation.

### REL-01: Version consistency
- Authoritative version maintained in `app/build.gradle.kts` (`versionCode` and `versionName`).
- Bumping version on every push.
- `README.md` download links, badges, and release asset names kept strictly in sync.

### REL-02: Release artifact verification
- Automated release workflow in `.github/workflows/release.yml`.
- `release-policy.py` validates tag naming and branch integrity.
- Post-publish asset verification via `verify-release.sh`.
- Zero 404 links on release APK downloads.
