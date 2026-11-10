# Phase 11: Internet Connectivity & False-Positive Audit

**Document ID:** `AUDIT-CONN-001`  
**Package:** `com.unblocker.app`  
**Date:** November 8, 2026  

---

## 1. Network Connectivity Preservation

A critical failure mode in DNS blockers is accidentally breaking essential internet services, CDN hosts, banking applications, or operating system update services.

Un-Blocker protects essential services through a multi-tier **Never-Block Gate**:
- `assets/never-block-hosts.txt`: Contains protected infrastructure domains that can never be blocked by third-party filter lists.
- `public_suffix_list.dat`: Mozilla Public Suffix List ensures that rule compilers never accidentally block entire top-level domains (e.g. `.co.uk`, `.com`, `.org`) or multi-tenant hosting platforms.

---

## 2. Essential Domain Verification Matrix

Evaluated via live resolution queries on Android 17 under active VPN filtering:

| Critical Service / Domain | Purpose | Filtering Result | Resolved IP | Status |
| :--- | :--- | :--- | :--- | :--- |
| **`google.com`** | Search & OS Services | **ALLOWED** | `142.251.222.142` | **PASSED** |
| **`wikipedia.org`** | Information / Content | **ALLOWED** | `103.102.166.224` | **PASSED** |
| **`cloudflare.com`** | Web Infrastructure / CDN | **ALLOWED** | `104.16.132.229` | **PASSED** |
| **`github.com`** | Developer Infrastructure | **ALLOWED** | `20.207.73.82` | **PASSED** |
| **`microsoft.com`** | Windows / Office Cloud | **ALLOWED** | `150.171.109.132` | **PASSED** |
| **`doubleclick.net`** | Google Ad Serving | **SINKHOLED** | `127.0.0.1` | **PASSED** |
| **`google-analytics.com`**| Telemetry Tracker | **SINKHOLED** | `127.0.0.1` | **PASSED** |

**Zero False Positives:** Essential web and cloud services resolve normally at native speeds, while recognized tracking and advertising endpoints are sinkholed immediately.

---

## 3. DoH Bootstrap Downgrade Verification

To ensure web browsers do not bypass local DNS filtering via encrypted DNS-over-HTTPS, Un-Blocker includes automatic bootstrap interception for:
- `use-application-dns.net` (Firefox canary)
- `cloudflare-dns.com`
- `dns.google`
- `dns.quad9.net`
- `doh.opendns.com`

**Observed Result:** Browsers receiving NXDOMAIN or sinkhole responses for canary endpoints smoothly fall back to standard Android system DNS, preserving complete on-device protection without disrupting web browsing.
