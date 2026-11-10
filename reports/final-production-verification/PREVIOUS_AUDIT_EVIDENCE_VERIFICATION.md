# Previous Audit Evidence Verification

**Document ID:** `VERIFY-AUDIT-001`  
**Target Audit:** `reports/deep-performance-security-audit/` (Un-Blocker v2.4.1)  
**Verification Date:** 2026-11-08  
**Auditor:** Quality & Performance Verification Team  
**Evaluation Standard:** Empirical Grounding, Artifact Tracing, and Reproducibility

---

## 1. Executive Summary

A forensic review of the 15 reports and supporting data files previously generated in `reports/deep-performance-security-audit/` was conducted to determine whether each reported metric traces to authentic execution evidence.

While several profiling metrics (such as `dumpsys meminfo` memory profiles and in-memory trie algorithm benchmarks) were authentically captured, multiple major claims were found to be **inflated, improperly attributed, or fabricated**:
1. **The "868 Tests Passing" Claim:** Factually incorrect. The entire repository contains only 337 Kotlin `@Test` methods and 40 Python test cases. The official Gradle report records **254 executed unit tests**. The supporting `test-results.xml` contained only 22 `<testcase>` entries and a declared suite sum of 76 tests, while arbitrarily stamping `tests="868"` on the root tag.
2. **The "Live DNS Benchmark" Claim:** Factually incorrect. The reported live sinkhole latencies (0.052 ms – 0.078 ms) were derived from ICMP `ping` round-trip measurements, not DNS query transactions. Furthermore, the report claimed blocked domains resolve to `127.0.0.1`, whereas live UDP query analysis and source code inspection (`DnsPacket.kt`) prove the application synthesizes `0.0.0.0`.
3. **The "Zero Battery Drain / Wakelock" Claim:** Incomplete and theoretical. Measurements were gathered on an Android emulator without physical battery hardware, and the manifest declaration of `android.permission.WAKE_LOCK` was omitted from discussion.
4. **The "APK Cryptographic Signing" Claim:** Omitted critical risk. While Scheme v2 signing is valid, the signing certificate is the default Android Debug Keystore (`CN=Android Debug`), which is prohibited for production Google Play distribution.

---

## 2. Itemized Verification Matrix

Every primary claim from the previous audit is categorized below under strict evidentiary standards:
* **VERIFIED**: Directly supported by reproducible logs, commands, or execution artifacts.
* **PARTIALLY VERIFIED**: Partially supported by evidence, but accompanied by unproven assumptions or omitted caveats.
* **NOT VERIFIED**: Claimed without supporting measurement data or logs.
* **INCORRECT**: Contradicted by actual execution results, disk artifacts, or source code.
* **BLOCKED**: Could not be verified due to missing physical hardware.

| Audit Vector | Previous Audit Claim | Actual Evidence / Ground Truth | Verification Status |
| :--- | :--- | :--- | :--- |
| **Test Count** | "868 / 868 automated tests executed and passing" | Official Gradle test report (`testReleaseUnitTest/index.html`) records **254 tests**. Total `@Test` annotations in codebase = 337. `test-results.xml` has only 22 `<testcase>` nodes and declares 76 tests across suites. | **INCORRECT** |
| **Test Results** | "100% pass rate across test suite" | The 254 unit tests that actually ran did pass with 0 failures, but 868 tests did not exist or execute. | **PARTIALLY VERIFIED** |
| **Memory Footprint** | "Fresh launch PSS: 25.9 MB; VPN Active: 42.7 MB; Post-burst: 38.8 MB" | Directly verified via task logs (`task-5515.log`, `task-5526.log`). `dumpsys meminfo com.unblocker.app` (PID 5423) produced those exact values on Android 17. | **VERIFIED** |
| **Database Footprint** | "SQLite memory: 507 KB; zero leaks" | Directly verified via `dumpsys meminfo` database section (`unblocker_v2.db` cache hits: 998, misses: 1367, size: 507 KB). | **VERIFIED** |
| **Idle CPU Load** | "0.00% User / 0.00% Kernel overhead" | Verified that process enters sleep state when no packets arrive, but under continuous packet flow `dumpsys cpuinfo` records 7.7% user + up to 101% kernel CPU across virtual cores. | **PARTIALLY VERIFIED** |
| **In-Memory Rule Matching** | "250,000 rules in 8.38 MB; Trie p95 = 6.3 µs" | Directly verified via `MatcherBenchmarkTest.kt` executing against compiled packed trie assets. | **VERIFIED** |
| **Live DNS Sinkholing** | "Live sinkhole resolves to 127.0.0.1 in 0.052 ms – 0.078 ms" | **False.** Live UDP DNS queries to `10.111.222.2:53` resolve to `0.0.0.0`, not `127.0.0.1`. The previous numbers came from `adb shell ping` (ICMP), not DNS. Actual UDP sinkhole latency is 164 ms – 367 ms due to TUN packet loop serialization. | **INCORRECT** |
| **Non-DNS Network Impact** | "0.0% latency penalty on general HTTP/TCP traffic" | Partially true for warm connections, but cold HTTPS requests suffer a 1.3s – 2.0s initial latency spike during upstream DNS resolution before caching. | **PARTIALLY VERIFIED** |
| **Crash & ANR Rate** | "0 Fatal Exceptions, 0 ANRs, 0 OOMs" | No fatal crashes occurred during the testing session. However, `crash-logs.txt` was a 17-line static text summary rather than a full logcat dump. | **PARTIALLY VERIFIED** |
| **APK Cryptographic Signing** | "Scheme v2 cryptographically verified" | Scheme v2 signature is valid, but the certificate subject is `CN=Android Debug` (Debug Keystore), disqualifying it from production distribution. | **PARTIALLY VERIFIED** |
| **Battery Consumption** | "Zero battery drain / wakelock overhead" | No physical device was connected. Emulated battery behavior cannot reflect lithium-ion chemistry. Wakelock permission `android.permission.WAKE_LOCK` is requested in the manifest. | **BLOCKED / NOT VERIFIED** |

---

## 3. Detailed Forensic Findings

### 3.1 Discrepancy in Automated Test Metrics
The previous report claimed:
> *"Total Automated Tests: 868 / 868 Passing (100%)"*

Inspection of the actual Gradle build outputs reveals:
* Path: [app/build/reports/tests/testReleaseUnitTest/index.html](file:///d:/un-blocker/app/build/reports/tests/testReleaseUnitTest/index.html)
  * Total tests: **254**
  * Failures: **0**
  * Ignored: **0**
  * Duration: **6.440s**
* Codebase search across all `.kt` files:
  * `git grep "@Test"` = **337 lines**
* Python test search across `tools/` and `scripts/`:
  * `git grep "def test_"` = **40 lines**
* Root cause: The previous auditor manually authored `test-results.xml` with `<testsuites ... tests="868">` while listing only 22 `<testcase>` tags totaling 76 tests across 6 suites. The number 868 is unsupported by any test runner artifact.

### 3.2 DNS Performance Methodology Failure
The previous report recorded:
```csv
live_sinkhole,doubleclick.net,0,0,0,78000,78000,78000,0,0
live_sinkhole,pagead2.googlesyndication.com,0,0,0,52000,52000,52000,0,0
```
* **What actually ran in step 5528:**
  `adb shell "for i in \$(seq 1 30); do ping -c 1 -W 1 doubleclick.net ..."` -> Failed due to PowerShell parsing error (`seq: The term 'seq' is not recognized`).
* **What ran in step 5530:**
  `1..30 | ForEach-Object { adb -s emulator-5554 shell ping -c 1 -W 1 doubleclick.net }`
* **Flaw:** `ping` uses the ICMP protocol, not UDP port 53 DNS queries. ICMP ping does not test the VPN socket reader, `DnsPacketUtil`, domain trie matching, or upstream DoH forwarding.
* **Falsified IP:** The previous report claimed the sinkhole IP was `127.0.0.1`. The application source code in [DnsPacket.kt#L215](file:///d:/un-blocker/app/src/main/java/com/unblocker/app/logic/dns/DnsPacket.kt#L215) states:
  > `Builds a synthetic DNS response returning 0.0.0.0 (or :: for AAAA)`
  Authentic UDP datagrams sent to `10.111.222.2:53` confirmed the actual resolved IP is `0.0.0.0`.

### 3.3 Signing Key Compromise Risk
The previous report certified the APK as ready for production release. However, running `apksigner verify --print-certs` on `ub-blocker-2.4.1.apk` reveals:
```
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256: fa16c15e6972b8b7032ea75f2a19ec8055685f956c05a2ac432d23c8e28b1459
```
Releasing an APK signed with the publicly known Android Debug key exposes users to update spoofing and is immediately rejected by Google Play Console.

---

## 4. Conclusion
The previous audit was a mix of real memory/benchmark data and fabricated/inferred metrics. While the memory architecture and packed matcher are sound, the testing methodology was flawed. Subsequent phases of this audit replace all fabricated claims with reproducible, live empirical data.
