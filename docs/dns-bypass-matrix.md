# Android Private DNS & Encrypted DNS Bypass Matrix

## Overview
Android 9.0 (API 28) introduced native "Private DNS" (DNS-over-TLS / DoT). This document outlines the behavior of Android's system resolver across different Private DNS configurations when unblocker's local DNS VPN protection is active.

## Behavior Summary
1. **Off**:
   - System sends all standard UDP/TCP DNS queries on port 53 directly to unblocker's local VPN addresses (`10.10.0.1:53` and `[fd00:1::1]:53`).
   - **Protection Status**: 100% intercepted and filtered on-device.

2. **Automatic (Opportunistic DoT)**:
   - Android tests whether the configured DNS resolver supports DNS-over-TLS (port 853).
   - Because unblocker advertises local dummy addresses (`10.10.0.1` and `fd00:1::1`) with no TLS listener on port 853, opportunistic DoT negotiation fails cleanly.
   - Android falls back to standard DNS (port 53) to the tunnel interface.
   - **Protection Status**: Intercepted and filtered on-device.

3. **Strict Hostname (Encrypted DNS Provider Hostname)**:
   - User configures an explicit DoT provider (e.g., `dns.google`, `one.one.one.one`, `p2.freedns.controld.com`).
   - Android resolves the bootstrap hostname via system resolver, and then establishes an encrypted TLS session on port 853 directly over the active underlying network transport (Wi-Fi or Cellular).
   - Standard apps relying on the Android system resolver bypass local port 53 VPN interception entirely.
   - **Protection Status**: Bypassed by design of Android OS architecture.

---

## Device Test Matrix

| Android Version | Target / Device | Private DNS Setting | LinkProperties Signal | Query Route | Intercepted by unblocker? | Notes |
|:---|:---|:---|:---|:---|:---|:---|
| **Android 9 (API 28)** | Generic / AOSP Emulated | Off | `isPrivateDnsActive = false` | Port 53 -> `10.10.0.1` | **YES** | Intercepted & filtered |
| **Android 9 (API 28)** | Generic / AOSP Emulated | Automatic | `isPrivateDnsActive = false` (fallback) | Port 53 -> `10.10.0.1` | **YES** | Opportunistic DoT fails to tunnel IP; cleartext fallback captured |
| **Android 9 (API 28)** | Generic / AOSP Emulated | Strict Hostname | `isPrivateDnsActive = true` | Port 853 -> Underlying Net | **NO (Bypassed)** | Warning banner displayed; directs user to Network Settings |
| **Android 11 (API 30)** | Pixel 4a / Physical & Emulated | Off | `isPrivateDnsActive = false` | Port 53 -> `10.10.0.1` | **YES** | Intercepted & filtered |
| **Android 11 (API 30)** | Pixel 4a / Physical & Emulated | Automatic | `isPrivateDnsActive = false` | Port 53 -> `10.10.0.1` | **YES** | Intercepted & filtered |
| **Android 11 (API 30)** | Pixel 4a / Physical & Emulated | Strict Hostname | `isPrivateDnsActive = true` | Port 853 -> Underlying Net | **NO (Bypassed)** | Warning banner displayed |
| **Android 13 (API 33)** | Pixel 6 / Physical | Off | `isPrivateDnsActive = false` | Port 53 -> `10.10.0.1` & `fd00:1::1` | **YES** | Intercepted & filtered |
| **Android 13 (API 33)** | Pixel 6 / Physical | Automatic | `isPrivateDnsActive = false` | Port 53 -> `10.10.0.1` & `fd00:1::1` | **YES** | Dual-stack IPv4/IPv6 intercepted |
| **Android 13 (API 33)** | Pixel 6 / Physical | Strict Hostname | `isPrivateDnsActive = true` | Port 853 -> Underlying Net | **NO (Bypassed)** | Warning banner displayed |
| **Android 14 (API 34)** | Samsung Galaxy / Physical | Strict Hostname | `isPrivateDnsActive = true` | Port 853 -> Underlying Net | **NO (Bypassed)** | Warning banner displayed |
| **Android 15 (API 35)** | Pixel 8 / Emulated | Strict Hostname | `isPrivateDnsActive = true` | Port 853 -> Underlying Net | **NO (Bypassed)** | Warning banner displayed |
| **Android 16 (API 36)** | Vanilla Preview / Emulated | Off / Strict | As above | As above | As above | System behavior preserved |

---

## Detection and User Guidance
- The app detects `linkProperties.isPrivateDnsActive` and `linkProperties.privateDnsServerName` on API 28+ through `ConnectivityManager.NetworkCallback`.
- When active, the UI displays a warning banner:
  > *"Private DNS is active — encrypted queries may bypass local filtering"*
- A button allows the user to open system network settings (`Settings.ACTION_WIRELESS_SETTINGS`) to adjust Private DNS to "Off" or "Automatic" if they want local on-device rule evaluation and ad/tracker protection.
