# 09 - Production Keystore & Release Signing Pipeline

## Goal
Document and configure the end-to-end production signing and verification pipeline for Google GitHub Actions release automation. Guide the repository owner through provisioning production cryptographic keys without risking secret leakage.

## Current State
- `.github/workflows/release.yml` implements a 3-job pipeline: `preflight`, `verify`, and `publish`.
- Test builds triggered by `test-v*` tags use test signing keys (`UB_BLOCKER_TEST_KEYSTORE_BASE64`) and successfully output debuggable APKs.
- Production builds triggered by `v*` tags require `UB_RELEASE_KEYSTORE_BASE64`, `UB_RELEASE_KEYSTORE_PASSWORD`, `UB_RELEASE_KEY_ALIAS`, `UB_RELEASE_KEY_PASSWORD`, and repository variable `UB_RELEASE_SIGNER_SHA256` in the `production` environment, which are not yet configured by the repository owner.

## Steps
1. Document the exact key generation process using standard `keytool`:
   ```bash
   keytool -genkeypair -v -keystore ub-release.keystore -alias unblocker-release \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Document base64 encoding procedure:
   ```bash
   base64 -w 0 ub-release.keystore > ub-release.keystore.base64
   ```
3. Document extraction of certificate fingerprint:
   ```bash
   keytool -list -v -keystore ub-release.keystore | grep "SHA256:"
   ```
4. Document the exact GitHub Environment secrets configuration under `Settings -> Environments -> production`.
5. Verify `scripts/release-policy.py secrets` and `scripts/verify-apk-signature.sh` scripts validate production APKs accurately without logging sensitive keys.

## Acceptance Criteria
- Full documented instructions provided for repository administrator.
- CI release policy tests pass.
- Release workflow is verified and ready for production key activation.

## Risks & Dependencies
- Secret variables must be set by repository owner with Admin permissions in GitHub repository settings.
