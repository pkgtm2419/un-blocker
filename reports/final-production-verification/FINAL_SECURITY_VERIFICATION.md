# Final Application Security Verification

**Document ID:** `SEC-VERIFY-001`  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Artifact Inspected:** `app/build/outputs/apk/release/ub-blocker-2.4.1.apk`  
**Audit Date:** 2026-11-08  
**Audit Standard:** OWASP Mobile Application Security Verification Standard (MASVS v2.0)

---

## 1. Executive Summary & Security Verdict

An exhaustive static and runtime security audit was performed on the exact release APK binary (`ub-blocker-2.4.1.apk`).

**Security Evaluation:**
* **Application Hardening:** **EXCELLENT**. ProGuard/R8 obfuscation is active, debuggable mode is disabled (`android:debuggable="false"`), application backup is prohibited (`android:allowBackup="false"`), cleartext HTTP traffic is blocked, and zero unexported components are accessible to third-party apps.
* **Release Signing Key Integrity:** **CRITICAL RELEASE BLOCKER**. Cryptographic signature analysis via `apksigner` revealed that the release APK was packaged using the **Android Debug Keystore (`CN=Android Debug`)**. While the APK passes cryptographic verification, it cannot be uploaded to Google Play and is susceptible to key-compromise update attacks if distributed publicly.

---

## 2. Static & Binary Analysis (APK Inspection)

### 2.1 Cryptographic Signing Analysis
* **Verification Command:** `apksigner verify --verbose --print-certs ub-blocker-2.4.1.apk`
* **Results:**
  ```
  Verified using v1 scheme (JAR signing): false
  Verified using v2 scheme (APK Signature Scheme v2): true
  Verified using v3 scheme: false
  Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
  Signer #1 certificate SHA-256 digest: fa16c15e6972b8b7032ea75f2a19ec8055685f956c05a2ac432d23c8e28b1459
  ```
* **Risk Evaluation:**
  > [!WARNING]
  > **Debug Certificate in Production Build:** The APK was compiled using debug signing credentials. Google Play Console will reject this artifact with error `APK signed with debug key`. Before release, the artifact must be signed using a production Keystore protected by CI/CD secrets.

### 2.2 Attack Surface & Component Exposure
* **Manifest Audit (`aapt2 dump xmltree`):**
  * **Exported Activities:** Exactly one (`com.unblocker.app.MainActivity`), which is required as the user launcher (`android.intent.category.LAUNCHER`).
  * **Exported Services:** Exactly zero. `UnblockerVpnService` is declared with `android:exported="false"` and guarded by `android.permission.BIND_VPN_SERVICE`.
  * **Exported Broadcast Receivers:** Exactly zero. `BootReceiver` is declared with `android:exported="false"`.
  * **Content Providers:** None declared.
* **Surface Exposure Score:** **0 unauthenticated entry points**. Third-party applications cannot bind, hijack, or invoke internal Un-Blocker components.

### 2.3 Data Protection & Storage Hardening
* **Backup Exfiltration Prevention:** `android:allowBackup="false"` is enforced. Running `adb backup -f backup.ab com.unblocker.app` is rejected by the Android OS package manager.
* **Cleartext Network Policy:** `android:usesCleartextTraffic="false"` is enforced. All general network operations require TLS/HTTPS.
* **Hardcoded Credentials Scan:** Recursive regex scanning across DEX strings and assets revealed zero API keys, auth tokens, client secrets, or private keys.
* **On-Device Database:** `unblocker_v2.db` stores only domain names, counters, and HMAC pseudonyms. No sensitive browsing history, full URLs, cookies, or user credentials are stored.

---

## 3. Runtime Privacy & Telemetry Inspection

* **Logcat Telemetry Leakage:** Monitored logcat output across 500+ query transactions. The release build contains zero `Log.d` or `Log.v` statements emitting domain names, client IP addresses, or packet bytes.
* **Outbound Sockets:** Audited via `netstat -an` on Android 17. The application maintains sockets strictly to:
  1. The local VPN TUN loopback interface (`10.111.222.1 <-> 10.111.222.2:53`).
  2. The upstream DNS resolver provided by Android NetworkCapabilities (`10.0.2.3:53`).
  * **Zero Analytics SDKs:** No Google Analytics, Firebase, Crashlytics, Mixpanel, or Facebook SDKs are bundled.
  * **Zero Remote Telemetry:** The app makes zero telemetry, tracking, or diagnostic network calls to any remote server.

---

## 4. Remediation Checklist for Production GA

| Vulnerability / Item | Severity | Remediation Action | Status |
| :--- | :--- | :--- | :--- |
| **Debug Certificate Used** | **CRITICAL** | Re-sign the APK using a secure production Keystore with Scheme v2/v3 signatures enabled. | **BLOCKER (Must Fix)** |
| **Wakelock Permission Declared** | Low | Verify if `WAKE_LOCK` can be stripped from `AndroidManifest.xml` if WorkManager jobs are strictly non-wakeful. | Advisory |
| **Screen-off DNS Timeout** | Medium | Implement an onboarding dialog requesting battery optimization exemption on Android 14+. | Advisory |

---

## 5. Conclusion
From a software and architectural standpoint, Un-Blocker v2.4.1 is secure, private, and resistant to on-device tampering. However, because `ub-blocker-2.4.1.apk` is signed with `CN=Android Debug`, it **cannot be approved as production-ready until re-signed with genuine release credentials**.
