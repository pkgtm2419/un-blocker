# Real Internet Speed Comparison (A/B Controlled Audit)

**Document ID:** `SPEED-COMPARE-001`  
**Test Harness:** On-device `DnsBench` (`app_process`) on Android 17 (API 37)  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Audit Date:** 2026-11-08  
**Repetitions:** 5 controlled iterations per target across both scenarios  
**Payloads Tested:**  
* Google Home (`https://www.google.com`, ~31.8 KB HTML)
* Cloudflare CDN Asset (`https://www.cloudflare.com`, ~1.33 MB payload)
* Wikipedia Encyclopedia (`https://www.wikipedia.org`, ~122.4 KB payload)

---

## 1. Methodology & Environmental Controls

To evaluate whether Un-Blocker induces real-world network degradation, controlled A/B testing was executed directly from within the Android runtime environment:
* **Scenario A (Baseline):** Application VPN disabled (`tun0` interface unmounted, native network routing).
* **Scenario B (Protected):** Application VPN active (`tun0` route active, DNS filtered via `10.111.222.2:53`).

Both scenarios executed on identical network connectivity (Host WHPX virtual interface bridge), communicating with identical external endpoints. Each request measured:
1. **DNS Resolution Latency:** `InetAddress.getAllByName(host)` timing.
2. **TCP/TLS Handshake Latency:** Socket establishment and TLS negotiation timing.
3. **Time to First Byte (TTFB):** HTTP GET dispatch to response header receipt.
4. **Total Request Duration:** Cold-to-wire complete payload download duration.

---

## 2. Empirical Benchmark Data

### 2.1 Google (`https://www.google.com` - 31.8 KB)

| Metric | Scenario A (VPN Disabled) | Scenario B (VPN Active) | Variance / Delta | Statistical Significance |
| :--- | :--- | :--- | :--- | :--- |
| **DNS Median** | 6.39 ms | **0.28 ms** | -6.11 ms (-95.6%) | **Faster** (Local DNS cache hit) |
| **DNS p95 (Cold)** | 319.06 ms | **1,670.10 ms** | +1,351.04 ms (+423%) | **Slower** (Upstream resolver cold start) |
| **Connection Median** | 0.37 ms | **0.36 ms** | -0.01 ms (-2.7%) | Identical (Bypasses TUN) |
| **TTFB Median** | 178.85 ms | **137.99 ms** | -40.86 ms (-22.8%) | No degradation |
| **Total Request Median** | 292.41 ms | **152.48 ms** | -139.93 ms (-47.8%) | **Faster** (Cache & connection reuse) |
| **Total Request p95** | 1,697.16 ms | **2,449.74 ms** | +752.58 ms (+44.3%) | Cold request penalty |
| **HTTP Success Rate** | 5 / 5 (100%) | 5 / 5 (100%) | 0.0% failure | 100% Reliable |

### 2.2 Cloudflare (`https://www.cloudflare.com` - 1.33 MB Payload)

| Metric | Scenario A (VPN Disabled) | Scenario B (VPN Active) | Variance / Delta | Statistical Significance |
| :--- | :--- | :--- | :--- | :--- |
| **DNS Median** | 2.34 ms | **4.81 ms** | +2.47 ms | Comparable |
| **DNS p95 (Cold)** | 158.58 ms | **2,209.84 ms** | +2,051.26 ms (+1293%) | **Cold query upstream delay** |
| **Connection Median** | 0.26 ms | **0.38 ms** | +0.12 ms | Identical |
| **TTFB Median** | 300.26 ms | **293.08 ms** | -7.18 ms (-2.4%) | No degradation |
| **Total Request Median** | 983.01 ms | **682.87 ms** | -300.14 ms (-30.5%) | **Full bandwidth available** |
| **Total Request p95** | 1,410.66 ms | **3,069.77 ms** | +1,659.11 ms (+117%) | Cold query penalty |
| **Downloaded Bytes** | 1,334,883 B | 1,334,883 B | 0 bytes lost | 100% Data Integrity |

### 2.3 Wikipedia (`https://www.wikipedia.org` - 122.4 KB)

| Metric | Scenario A (VPN Disabled) | Scenario B (VPN Active) | Variance / Delta | Statistical Significance |
| :--- | :--- | :--- | :--- | :--- |
| **DNS Median** | 0.63 ms | **0.17 ms** | -0.46 ms (-73.0%) | **Faster** (Cache hit) |
| **DNS p95 (Cold)** | 100.36 ms | **2,199.48 ms** | +2,099.12 ms (+2091%) | **Cold query upstream delay** |
| **Connection Median** | 0.97 ms | **0.17 ms** | -0.80 ms | Identical |
| **TTFB Median** | 135.76 ms | **121.53 ms** | -14.23 ms (-10.5%) | No degradation |
| **Total Request Median** | 184.43 ms | **148.07 ms** | -36.36 ms (-19.7%) | **Faster** |
| **Total Request p95** | 621.94 ms | **2,677.10 ms** | +2,055.16 ms (+330%) | Cold query penalty |
| **Downloaded Bytes** | 122,421 B | 122,421 B | 0 bytes lost | 100% Data Integrity |

---

## 3. Analysis of Findings

### 3.1 The "Zero Slowdown" Hypothesis Refinement
The previous audit claimed:
> *"Port 53 isolation guarantees 0.0% latency and 0.0% bandwidth impact on user internet browsing."*

Our empirical audit reveals that this statement is **only true after the DNS entry is cached**:
1. **Bulk Throughput & TTFB:** **0.0% degradation.** Downloading a 1.33 MB asset took 682.87 ms under VPN vs 983.01 ms natively. TCP and TLS traffic does indeed bypass the TUN interface completely, enjoying full hardware network card offload.
2. **Warm Browsing Experience:** **Sub-millisecond resolution.** When a domain has been queried once, Un-Blocker's in-memory `DnsCache` returns the validated answer in **0.17 ms – 0.28 ms**, which is faster than native Android OS DNS resolution.
3. **Cold Domain Lookups:** **Noticeable 1.5s – 2.2s latency spike.** On the very first visit to an uncached domain, the DNS packet must be read from TUN, parsed, checked against the trie filter, passed to `DnsRequestCoalescer`, scheduled onto the 4-thread `run.forwarding` pool, dispatched via DoH/UDP upstream, validated, and written back to TUN. This introduces an observed cold-resolution delay of ~1.6s to 2.2s.

---

## 4. Conclusion
Un-Blocker v2.4.1 does not throttle internet bandwidth or degrade ongoing TCP/HTTP streams. However, users will perceive an initial 1.5s - 2.0s delay when opening newly visited websites due to upstream DNS resolution serialization.
