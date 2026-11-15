---
name: release-verifier
description: Use when preparing a GitHub release, APK sign-off, version/tag change or validating a published artifact.
---

# Release Verifier

## Release checks
1. Read `docs/RELEASE_PROCESS.md`, existing `release.yml`, release-policy and signing verification scripts. Verify maintainer authorization and exact version/tag from Gradle and Git history.
2. Run JVM/Python/lint/APK/instrumentation where possible. If lint suppression exists, don't call warnings gated. Include security and privacy review.
3. Check datasets/license/third-party provenance, `gradle/verification-metadata.xml`, native binary gate, reproducibility as applicable.
4. Protect all signing secrets: never echo keystore passwords or commit certificates. Verify same signing identity for updates and intended production variant.
5. Preflight policy, tag and release asset hash/certificate; link live **verified** public APK asset. No invented success or silent overwrite.
6. Update `CHANGELOG.md`, README badge/download text, release notes, actual platform coverage and test results. Docs sync job is not a release publisher.

## Example
Reject an APK release page claiming v2.6.1 if its built manifest version, tag or signing certificate differ.

## Mistake
Equating `assembleReleaseTest` (development signing) with production release signing.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
