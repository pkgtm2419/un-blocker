# Phase 10: Defensive Security & User Privacy Audit

**Document ID:** `AUDIT-SEC-001`  
**Package:** `com.unblocker.app`  
**Standard:** OWASP Mobile Application Security Verification Standard (MASVS v2.0)  
**Date:** November 8, 2026  

---

## 1. Executive Security & Privacy Overview

Un-Blocker was audited for defense-in-depth security, IPC attack surface exposure, cryptographic strength, data persistence, and zero-telemetry enforcement.

| Security Domain | Findings / Implementation | Rating |
| :--- | :--- | :--- |
| **Component IPC Security** | Only `MainActivity` exported; `UnblockerVpnService` and `BootReceiver` non-exported (`exported="false"`). | **EXCELLENT** |
| **Intent Security** | All notification and service intents use `FLAG_IMMUTABLE`. | **COMPLIANT** |
| **Cleartext Policy** | `android:usesCleartextTraffic="false"` explicitly enforced. | **COMPLIANT** |
| **Backup Invalidation** | `allowBackup="false"`; `data_extraction_rules.xml` excludes all databases, files, and preferences from cloud/device backups. | **COMPLIANT** |
| **Logcat Privacy** | **Zero `Log.*` calls and zero `println` calls** across `app/src/main`, `vpn-engine`, and `data-store`. | **EXCEPTIONAL** |
| **Cryptographic Storage** | Hardware-backed `AndroidKeyStore` with HMAC-SHA256 for private token generation. | **COMPLIANT** |
| **Third-Party Telemetry** | **Zero analytics SDKs**, zero tracking libraries, zero remote crash uploaders. | **100% PRIVATE** |

---

## 2. Privacy-by-Design Verification (`PRIVACY.md`)

The implementation was checked line-by-line against the public promises in `PRIVACY.md`:
1. **Promise:** No external tracking or telemetry.
   - **Verification:** Audited Gradle dependencies across all 5 submodules. Zero Google Analytics, Firebase, Crashlytics, Adjust, AppsFlyer, or Facebook SDKs are present.
2. **Promise:** No plaintext domain logging to disk.
   - **Verification:** Database stores aggregated counters (`domain_stats` with `call_count`, `last_seen`). Full packet payloads, timestamps of individual queries, and full browsing histories are never persisted.
3. **Promise:** No account or cloud sync.
   - **Verification:** The application contains zero user login flows, OAuth tokens, or remote account APIs. Everything remains strictly local to the device.

---

## 3. Cryptographic Enclave Implementation

- Evaluated `DeviceLearning.kt`:
  ```kotlin
  val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
      "AndroidKeyStore"
  )
  ```
- Keys are generated within the hardware-isolated Trusted Execution Environment (TEE) or StrongBox security enclave when available on the hardware.
- HMAC-SHA256 tokens ensure that learned domain patterns cannot be reversed into plaintext browsing records if the database file is extracted from the device.
