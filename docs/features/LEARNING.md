# On-device learning and evidence

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/logic/analysis/DeviceLearning.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/PrivateEvidenceStore.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/LocalNetworkLearner.kt`
- `ml-engine/src/main/java/com/unblocker/ml/`

## Current functional contract and verification

Adaptive learning uses bounded, independent evidence windows and structural signals without remote inference. Keystore HMAC identifiers and coarse evidence are stored in private no-backup storage; no raw learning-host list can be reconstructed for display. `:ml-engine` also contains heuristic batch components, whose production scheduling requires caller verification.

**Regressions:** expired/weak evidence, never-block infrastructure, no install-age-only blocking, key rotation and v1-v3 compatibility, reset, no plaintext learned names and bounded memory/CPU.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
