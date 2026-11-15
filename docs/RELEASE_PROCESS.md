# Release and signing process

**Read existing automation before changing anything:** `.github/workflows/release.yml`, `scripts/release-policy.py`, `scripts/verify-release.sh`, `scripts/verify-apk-signature.sh`, `scripts/check-release-native-libs.py`, `scripts/check-no-binaries.sh` and root `CONTRIBUTING.md`.

## Release gates

**Enforced signing policy (R14 resolved):** `.github/workflows/release.yml` strictly enforces production signing requirements for `v*` tags. If release keystore secrets or the `UB_RELEASE_SIGNER_SHA256` digest variable are not configured, the workflow fails closed during preflight checks without attempting test-key fallback. The production APK is built via `assembleRelease` and verified to be non-debuggable and signed by the pinned production key before and after publish. Test tags (`test-v*`) build `assembleDebug` using test keys.

1. Release scope and version match Gradle `versionCode/versionName`, signed release tag and documented release notes. Do not copy stale README version strings.
2. Full JVM tests, Python compiler tests, lint (note existing lint suppression), instrumentation on relevant Android APIs, ABI/R8 release-smoke checks and nonbinary/secret gates.
3. Verify dataset provenance and third-party notices; no unknown native library or generated binary checked into source.
4. Run `python3 scripts/release-policy.py preflight --tag v<version>` according to the workflow's expected environment; pass `--github-output` in Actions. Do not fabricate tag/version results.
5. Use approved keystore secrets only from maintainers' CI setup; never echo/store secrets, commit keystores, or replace signing identity for an update.
6. Verify actual APK signature/certificate, asset hash, version, minimum SDK, GitHub tag/asset URL and immutable artifact status **after** publish. A workflow badge alone is not evidence.
7. For each release, record actual tested devices, required permissions, security disclosures and known limitations in changelog/release notes.

## Branching

Work from a feature branch and PR into `main`. The new documentation-sync automation opens a separate PR only; it is **not** part of the APK signing path and cannot publish releases. Do not bypass branch protections for documentation changes.
