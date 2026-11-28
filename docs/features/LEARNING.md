# On-device learning and evidence

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/logic/analysis/DeviceLearning.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/PrivateEvidenceStore.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/LocalNetworkLearner.kt`
- `app/src/main/java/com/unblocker/app/services/RecordingSessionManager.kt`
- `app/src/main/java/com/unblocker/app/services/VpnBuilderPolicy.kt`
- `app/src/main/java/com/unblocker/app/ui/learn/LearnReviewPolicy.kt`
- `app/src/main/java/com/unblocker/app/ui/learn/LearnViewModel.kt`
- `ml-engine/src/main/java/com/unblocker/ml/`

## Current functional contract and verification

Adaptive learning uses bounded, independent evidence windows and structural signals without remote inference. Keystore HMAC identifiers and coarse evidence are stored in private no-backup storage; no raw learning-host list can be reconstructed for display. `:ml-engine` also contains heuristic batch components, whose production scheduling requires caller verification.

**Learn an App (isolated recording session & review, WP-02 / WP-03):** When a session starts, `UnblockerVpnService` rebuilds the tunnel via `VpnBuilderPolicy` calling `Builder.addAllowedApplication(targetPackage)` with no `addDisallowedApplication` calls (mutually exclusive). All intercepted DNS originates from the target package by construction, so `RecordingSessionManager` records every query seen without UID-mismatch filtering (`UidResolver` remains unused on the hot path / diagnostic only). A persistent banner warns that other apps are unfiltered during learning (max 10 min), and a 60 s inactivity banner explains encrypted DNS bypass with a link to `docs/dns-bypass-matrix.md`. Normal tunnel routing is restored on stop, timeout, `onCleared()`, crash, and startup (`IsolatedSessionRecoveryPolicy` + persisted `isolatedSessionActive`).

During rule review (`LearnReviewPolicy`), classification uses whole DNS labels and public-suffix boundaries rather than substring containment (`threads.net`, `downloads.example.com`, `broadcast.example.com` do not false-match trackers). Every row defaults to `ReviewAction.KEEP_DEFAULT` (no pre-selected ALLOW or BLOCK), with an explicit "Add all list matches" button. ALLOW submissions default to exact-host rules (`UserRuleGateway.set`), disabling the whole-site promotion switch for shared platforms (`amazonaws.com`, `cloudfront.net`, `googleapis.com`, `github.io`, etc.) and refusing ad-domain allows unless confirmed with a second explicit tap. Finished in-memory sessions surface "Also seen in: <apps>" indicators.

**Regressions:** expired/weak evidence, never-block infrastructure, no install-age-only blocking, key rotation and v1-v3 compatibility, reset, no plaintext learned names, bounded memory/CPU, isolated vs normal routing mutual exclusivity (`VpnBuilderPolicyTest`), recording without UID dropping (`RecordingSessionManagerTest`), whole-label review categorization (`LearnCategorizerTest`), zero-preselection & ad-allow refusal (`LearnApplyRulesTest`; device dual-app isolation: `NOT RUN`).

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
