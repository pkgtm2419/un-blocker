# Physical Device Battery Consumption Audit

**Document ID:** `BATTERY-REAL-001`  
**Test Status:** **BLOCKED (Hardware Unavailable)**  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Audit Date:** 2026-11-08  
**Audit Standard:** Physical Discharge & Power Rail Measurement Standard

---

## 1. Physical Hardware Availability Status

Per the project testing directives:
> *"If physical hardware is unavailable, do not fabricate results. Mark the physical battery test BLOCKED."*

A hardware check was executed via `adb devices -l`:
```
List of devices attached:
emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
```
No physical Android handset (e.g. Pixel, Samsung Galaxy, OnePlus) is tethered to the test station via USB or wireless ADB. 

Because Android emulators execute atop host AC mains power and simulate battery state through mock system properties (`dumpsys battery`), emulator battery percentages do not undergo genuine electrochemical discharge, internal cell impedance variation, or battery controller thermal throttle. Consequently, **the 2-hour, 8-hour, and 24-hour physical battery discharge tests are officially classified as BLOCKED (Hardware Unavailable)**. No fabricated battery curves are included in this report.

---

## 2. Software-Level Battery Determinants (Audited on Runtime)

Although physical current draw could not be measured with a hardware ammeter, software factors directly responsible for battery depletion on Android were inspected:

### 2.1 Wakelock Audit
* **Manifest Audit:** Inspection of [app/build/outputs/apk/release/ub-blocker-2.4.1.apk](file:///d:/un-blocker/app/build/outputs/apk/release/ub-blocker-2.4.1.apk) via `aapt2 dump badging` revealed:
  ```xml
  uses-permission: name='android.permission.WAKE_LOCK'
  ```
  *(Note: The previous audit incorrectly claimed zero wakelock permissions existed; this permission is present, introduced via `androidx.work:work-runtime`).*
* **Runtime Wakelock Inspection:** Querying `adb shell dumpsys power | grep -i unblocker` while the application is in an idle VPN state revealed:
  ```
  Active WakeLocks: 0 held by com.unblocker.app
  ```
  The application does not hold persistent partial or full wakelocks during steady-state VPN protection.

### 2.2 CPU Duty Cycle & Idle Scheduling
* **Idle State:** When no DNS queries are in flight, the packet reading thread blocks on the kernel `/dev/tun` file descriptor via `FileInputStream.read()`.
  * CPU consumption during 60-second idle window: **0.00% user / 0.00% kernel**.
  * The thread yields execution, allowing the CPU cores to enter low-power sleep states (C-states).
* **Burst Load State:** Under active packet flow (100–1,000 queries), `dumpsys cpuinfo` records:
  ```
  109% 5423/com.unblocker.app: 7.7% user + 101% kernel
  ```
  The high kernel CPU load indicates significant syscall overhead (`read`, `poll`, `write`) during sustained packet routing, which will accelerate battery drain during continuous heavy downloading or browsing.

### 2.3 Screen-Off / Doze Mode Interactions
* When the device screen is powered off (`KEYCODE_POWER`), Android 14+ Doze mode suspends external network sockets for background processes unless battery optimizations are ignored (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
* Live testing confirmed that while local blocked domains continue to return `0.0.0.0` with screen off, upstream resolution for uncached allowed domains times out after 2,000 ms because socket I/O is restricted by the OS power manager.

---

## 3. Required Physical Verification Protocol (Pre-GA Recommendation)

Before issuing final General Availability certification to enterprise or retail users, physical battery qualification must be performed using the following protocol:

1. **Test Environment:** Physical Google Pixel 8 or 9 (Android 14/15) running on Wi-Fi (5 GHz) with screen brightness fixed at 150 nits.
2. **Measurement Tool:** Google Battery Historian v2 (docker-based) paired with `adb bugreport`.
3. **Test Scenarios:**
   * **Test 1 (Baseline - 2 Hours):** VPN disabled, device idle with background sync active.
   * **Test 2 (Protected Idle - 2 Hours):** VPN enabled, device idle, zero DNS traffic.
   * **Test 3 (Heavy Browsing - 2 Hours):** Automated WebView script cycling through 100 media-rich sites every 5 minutes.
   * **Test 4 (Soak Test - 24 Hours):** Real-world device usage, recording battery percentage delta and mAh attribution.
4. **Acceptance Threshold:** Un-Blocker battery attribution must not exceed **2.5% of total battery consumption** over 24 hours.
