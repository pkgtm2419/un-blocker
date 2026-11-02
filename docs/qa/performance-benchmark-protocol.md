# Un-Blocker Performance Benchmark Protocol

**Date:** 2026-11-02  
**Purpose:** Standardize reproducible performance, battery, CPU, and DNS throughput benchmarks across physical Android devices and emulators.

## 1. Test Environment Controls
1. **Devices:**
   - Low-end: <= 4 GB RAM, Android 8.0 / 10 / 11
   - Modern: Pixel / Galaxy, Android 14 / 15 (API 34/35)
   - Emulator: x86_64, API 35, 16 KiB page size enabled
2. **Network Condition:**
   - Wi-Fi 5 GHz, controlled upstream resolver (1.1.1.1 or mock DNS)
   - Screen brightness fixed at 50%
   - Background sync and app updates disabled during measurement

## 2. Test Scenarios
- **Idle Baseline:** 2 hours with VPN active, 0 QPS, screen off
- **Steady Traffic:** 1 QPS and 10 QPS mixed synthetic queries (ad + benign)
- **Burst Traffic:** 10,000 queries burst over local TUN
- **Network Switch:** Wi-Fi to Cellular to Airplane mode recovery

## 3. Measured Metrics
- **PSS Memory:** `adb shell dumpsys meminfo com.unblocker.app`
- **CPU Time:** Thread time via Perfetto trace / `top`
- **Energy:** Power Profiler rail energy (mWh) or battery percentage drop per 1,000 queries
- **DNS Latency:** p50 and p95 query-response turnaround time (ms)
- **APK Footprint:** Total APK bytes and `.so` native library size
