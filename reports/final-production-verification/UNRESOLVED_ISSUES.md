# Unresolved Issues & Production Risk Log

**Document ID:** `UNRESOLVED-001`  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Audit Date:** 2026-11-08  
**Audit Scope:** Codebase Defects, Concurrency Bottlenecks, Key Management, and Operational Caveats

---

## 1. Executive Summary

This document logs all critical, high, and medium-priority issues, architectural bottlenecks, and unresolved operational constraints uncovered during the comprehensive follow-up audit.

Every issue is cataloged with root cause analysis, empirical failure proof, severity classification, and mandatory remediation steps.

---

## 2. Issue Inventory & Risk Analysis

### Issue 1: Release APK Signed with Android Debug Keystore
* **Classification:** **CRITICAL RELEASE BLOCKER**
* **Affected Artifact:** `app/build/outputs/apk/release/ub-blocker-2.4.1.apk`
* **Empirical Evidence:**
  Running `apksigner verify --print-certs ub-blocker-2.4.1.apk` outputs:
  ```
  Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
  Signer #1 certificate SHA-256 digest: fa16c15e6972b8b7032ea75f2a19ec8055685f956c05a2ac432d23c8e28b1459
  ```
* **Impact:**
  1. Google Play Console rejects debug-signed APKs during track upload.
  2. Users installing the APK are vulnerable to package-hijacking or malicious updates signed with the public Android debug certificate.
* **Remediation Action:**
  Re-sign the release APK using a dedicated, private production Keystore with APK Signature Scheme v2 and v3 enabled in the Gradle `signingConfigs.release` block.

---

### Issue 2: DNS Forwarding Concurrency Bottleneck (4 Threads / Queue 64)
* **Classification:** **HIGH ARCHITECTURAL DEFECT**
* **Affected File:** [TunnelRun.kt#L14](file:///d:/un-blocker/app/src/main/java/com/unblocker/app/services/TunnelRun.kt#L14)
* **Code Trace:**
  ```kotlin
  val forwarding = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(64))
  ```
* **Empirical Evidence:**
  Running 10 concurrent threads in `DnsBench dns-stress 100 10` resulted in:
  * **Success Rate:** 40.0% (40 / 100)
  * **Timeouts:** 59 queries timed out after 2,000 ms.
  * Logcat captured `RejectedExecutionException` triggered by queue capacity exhaustion in [UnblockerVpnService.kt#L400](file:///d:/un-blocker/app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt#L400).
* **Impact:**
  When a web page or application makes multiple simultaneous DNS queries across distinct uncached domains, queries are dropped or delayed, resulting in page load timeouts.
* **Remediation Action:**
  Increase the `ThreadPoolExecutor` core/max size to `Runtime.getRuntime().availableProcessors() * 2` (or min 8) and expand the `ArrayBlockingQueue` capacity from 64 to 256.

---

### Issue 3: Android 14+ Doze Mode Socket Suspension During Screen-Off
* **Classification:** **MEDIUM OPERATIONAL CONSTRAINT**
* **Affected Subsystem:** `UnblockerVpnService` background networking
* **Empirical Evidence:**
  When the device screen is powered off (`input keyevent 26`), queries to allowed domains that require upstream DoH/UDP resolution time out after 2,000 ms because Android restricts background socket communication.
* **Impact:**
  Background apps (podcasts, email sync, messaging) may fail to resolve new domains when the device screen has been off for several minutes.
* **Remediation Action:**
  Add a user prompt requesting exemption from battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so background socket routing remains unthrottled during Doze.

---

### Issue 4: Cold-Start DNS Resolution Latency Penalty
* **Classification:** **MEDIUM PERFORMANCE DEFECT**
* **Empirical Evidence:**
  A/B internet speed benchmarking revealed:
  * Native DNS resolution p95: **158 ms – 319 ms**
  * Un-Blocker cold DNS resolution p95: **1,670 ms – 2,209 ms**
* **Impact:**
  First-time visits to uncached websites feel visibly sluggish for 1.5–2 seconds while Un-Blocker performs upstream discovery, CNAME uncloaking, and cache validation.
* **Remediation Action:**
  Implement pre-fetching for common system domains and parallelize upstream query dispatch across multiple resolvers.

---

### Issue 5: Physical Hardware Battery Verification Pending
* **Classification:** **EVIDENTIARY LIMITATION**
* **Status:** Marked **BLOCKED** per project instructions due to lack of connected physical hardware.
* **Impact:**
  Software metrics show 0 active wakelocks and 0% idle CPU, but actual milliampere-hour (mAh) battery draw on lithium-ion cells remains unmeasured.

---

## 3. Remediation Matrix

| Issue ID | Description | Severity | Target Milestone | Blocker for GA? |
| :--- | :--- | :--- | :--- | :--- |
| **ISSUE-01** | Production Signing with Debug Key | CRITICAL | Immediate (Pre-release) | **YES** |
| **ISSUE-02** | Forwarding Queue Saturation (4 threads / 64 queue) | HIGH | v2.4.2 Stabilization | Advisory / High Load |
| **ISSUE-03** | Android 14+ Doze Socket Suspension | MEDIUM | v2.4.2 UX Polish | No |
| **ISSUE-04** | Cold DNS Resolution Delay (1.6s - 2.2s) | MEDIUM | v2.5.0 Optimization | No |
| **ISSUE-05** | Physical Device Battery Qualification | MEDIUM | Pre-GA Hardware Lab | Advisory |
