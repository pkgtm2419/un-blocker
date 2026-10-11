# 02 - Insecure Storage & Unsafe Input Hardening

## Goal
Audit and harden all data storage and input handling in the application. Ensure user preferences, domain inputs, learning models, and Room databases are protected against tampering, injection, and unauthorized data leakage.

## Current State
- `FilteringPreferences.kt` uses Android `Context.MODE_PRIVATE` `SharedPreferences`.
- `DeviceLearning.kt`, `PrivateEvidenceStore.kt`, and `PrivateDomainSet.kt` store cryptographic HMAC-SHA256 signatures derived using an Android Keystore `SecretKey` inside `context.noBackupFilesDir`.
- Domain rules in the UI accept user text via `OutlinedTextField` and normalize via `DomainName.normalize(domain)`.
- Room database `UnblockerDatabase` stores domain statistics and event entities with schema v2 (`2.json`).
- `allowBackup="false"` is set in `AndroidManifest.xml` alongside explicit `data_extraction_rules.xml` and `backup_rules.xml`.

## Steps
1. Audit `FilteringPreferences` to ensure default flags, reset routines, and keys are safe and strictly confined to private storage.
2. Verify domain input sanitization in `DomainName.kt` to prevent regex injection, control character bypasses, or malformed punycode/IDN attacks.
3. Validate that `Room` database queries in `DomainLogDao.kt` use parameter binding exclusively (no raw concatenated SQL strings).
4. Verify Android Keystore alias generation and key rotation behavior in `DeviceLearning.kt`.
5. Ensure error messages exposed to the UI never leak raw stack traces or internal storage paths.

## Acceptance Criteria
- All domain inputs submitted in the UI are strictly normalized and validated through `DomainName.normalize()`.
- Room DAO queries remain 100% parameter-bound with zero SQL injection vectors.
- Keystore access uses hardware-backed AndroidKeyStore provider where available.
- No sensitive user telemetry or unhashed domain names are written into persistent learner stores.

## Risks & Dependencies
- `DomainName.normalize` must maintain compatibility with valid internationalized domain names (IDN/punycode) while rejecting malicious inputs.
