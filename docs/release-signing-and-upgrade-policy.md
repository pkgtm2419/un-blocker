# Release Signing and Upgrade Policy

## 1. Overview and Key Separation

Un-Blocker enforces strict cryptographic separation between production releases and test builds:

- **Production Builds (`vX.Y.Z` tags):**
  - Built with Gradle task `assembleRelease`.
  - Must be signed with the dedicated production keystore (`UB_RELEASE_*` secrets in the `production` GitHub Environment).
  - Pinned by SHA-256 signer certificate fingerprint `UB_RELEASE_SIGNER_SHA256`.
  - Must not be `application-debuggable`.
  - Fail-closed policy: If production keystore secrets are missing or incomplete, the release CI workflow aborts immediately with exit code 1. **No fallback to test signing is permitted.**

- **Test / Prerelease Builds (`test-vX.Y.Z` tags):**
  - Built with Gradle task `assembleDebug`.
  - Signed with the stable test keystore (`UB_BLOCKER_TEST_*` secrets in the `test-release` environment).
  - Pinned by SHA-256 signer certificate fingerprint `UB_TEST_SIGNER_SHA256` (`0686161d8dc3051bfda2e03dd0406e1a6cddadf38c976f606bd8c474a872374e`).
  - Labeled explicitly as prereleases.

---

## 2. v2.2.0 Historical Audit and Upgrade Path

### The Issue in v2.2.0
In release v2.2.0, missing production keystore secrets in the GitHub workflow triggered an unintended fallback mechanism that copied `assembleReleaseTest` output as the release APK `ub-blocker-2.2.0.apk`. As a result, the published v2.2.0 APK was signed with the stable test key rather than the genuine production key.

### Upgrade Constraints on Android
Android's package manager strictly enforces signature consistency across application upgrades (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`):
1. **If transitioning to the official production key:**
   - Existing users running the test-signed v2.2.0 APK cannot perform an in-place upgrade to a production-signed APK without uninstalling the previous version first or utilizing APK Signature Scheme v3 key rotation with proof-of-rotation lineage.
   - The project must clearly notify users that upgrading from v2.2.0 to the official production release requires exporting rules / backing up preferences and performing a clean reinstallation.
2. **Never claim seamless in-place upgrade:**
   - Release notes and documentation must never claim seamless in-place upgrade when switching between test and production signing certificates.

---

## 3. Automated Verification Gates

Before any APK asset is published to GitHub Releases, the following checks are enforced by `scripts/verify-apk-signature.sh` and `scripts/release-policy.py`:
- `aapt2 dump badging`: confirms package name is `com.unblocker.app`, matching version name, and `!application-debuggable` for production builds.
- `apksigner verify --verbose`: validates APK signature schemes v2/v3 and asserts the exact expected certificate SHA-256 fingerprint.
- `scripts/check-release-native-libs.py`: asserts no unverified native `.so` files (e.g. unaligned 4 KiB TFLite binaries) are packaged in the release artifact.
- `SHA256SUMS`: generated and published alongside immutable release artifacts; duplicate uploads to an existing release tag are rejected.
