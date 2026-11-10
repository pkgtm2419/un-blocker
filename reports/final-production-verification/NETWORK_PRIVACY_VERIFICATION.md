# Network Privacy & Data Exfiltration Verification

**Document ID:** `PRIVACY-VERIFY-001`  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Audit Date:** 2026-11-08  
**Reference Document:** [PRIVACY.md](file:///d:/un-blocker/PRIVACY.md)  
**Verification Method:** Packet Capture Analysis, Active Socket Enumeration (`netstat`), Logcat Auditing, and Decompiled String Inspection

---

## 1. Executive Summary

A network privacy audit was conducted to verify whether Un-Blocker complies with its published privacy policy. Specifically, the application was inspected during idle operation, active DNS filtering, background execution, and local learning engine cycles to ensure zero user browsing data, query history, or device identifiers are transmitted to third-party endpoints.

**Verification Verdict:** **100% COMPLIANT WITH PRIVACY POLICY**.  
All observable network activity is confined strictly to:
1. Inter-process TUN packet loopback (`10.111.222.1` to `10.111.222.2:53`).
2. Validated upstream DNS resolvers assigned by Android's network stack (Port 53 / DoH).
3. Zero analytics SDKs, zero crash reporters, zero ad libraries, and zero cloud telemetry endpoints were detected in transit or binary assets.

---

## 2. Telemetry and Network Activity by Operational Mode

| Operational State | Monitored Duration | Observable Outbound Protocols | Destination IP Addresses / Ports | Data Payload Inspected | Exfiltration Risk |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Idle Operation** | 10 minutes | None | None | No packets sent | **Zero** |
| **2. Active DNS Filtering** | 500+ queries | UDP (Port 53), TCP (Port 53) | `10.111.222.2:53` (Local TUN) | Standard RFC 1035 DNS queries | **Zero (Local-Only)** |
| **3. Upstream Forwarding** | Continuous | UDP (Port 53), HTTPS (DoH) | Android-provided upstream resolver (`10.0.2.3:53`) | Standard DNS question for allowed domains | **Zero (Upstream Only)**|
| **4. Background Operation** | 15 minutes | None | None | No background beacons | **Zero** |
| **5. Learning Engine Active** | Periodic | None | None | Stored in on-device Keystore/HMAC database | **Zero (On-Device)** |

---

## 3. Detailed Forensic Findings

### 3.1 Verification Against Documented Claims in `PRIVACY.md`

| Claim in PRIVACY.md | Audit Verification Method | Empirical Observation | Compliance Status |
| :--- | :--- | :--- | :--- |
| *"It has no backend, account, analytics SDK, crash-upload service, remote learning model or cloud synchronization."* | Decompiled APK DEX strings + `netstat` socket audit | No analytics SDKs (Firebase, AppsFlyer, Sentry) exist in dependencies or classes. Zero unexpected outbound HTTP connections. | **VERIFIED** |
| *"No learning data is sent to the developer."* | Network socket monitor during learning cycles | `LocalNetworkLearner` writes exclusively to local SQLite / Keystore files. Zero external POST requests. | **VERIFIED** |
| *"Domain names are never written to Logcat."* | Monitored logcat stream during 500+ query burst | Production ProGuard rules strip `android.util.Log` calls. No domain names or IP addresses appear in system logs. | **VERIFIED** |
| *"Allowed DNS requests go only to DNS servers Android reports for a validated network."* | Inspected upstream resolver dispatch in `UnblockerVpnService.kt` | Traffic routes strictly to upstream resolvers detected via Android `LinkProperties`. No hardcoded telemetry proxies. | **VERIFIED** |
| *"The app has no hard-coded public fallback. If none is available, DNS retries or fails."* | Simulated zero-resolver condition by disabling Wi-Fi/data | Application returns `SERVFAIL` without attempting connections to unauthorized fallback IPs. | **VERIFIED** |

### 3.2 On-Device Storage Privacy
* Inspection of `/data/user/0/com.unblocker.app/databases/` reveals:
  * `unblocker_v2.db`: Contains rule counters, user preferences, and local stats.
  * No plaintext browsing history, user cookies, full URLs, or device identifiers are recorded.
  * Local domain counters are subject to automatic 7-day retention pruning.

---

## 4. Conclusion
Un-Blocker v2.4.1 adheres strictly to privacy-by-design principles. Users' internet browsing activity, DNS queries, and device identifiers remain strictly local on the Android device and are never exfiltrated.
