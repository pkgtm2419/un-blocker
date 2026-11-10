# Test Environment & Hardware Specification Documentation

**Document ID:** `AUDIT-ENV-001`  
**Date:** November 8, 2026  
**Auditor Operating Mode:** STRICT AUDIT-ONLY  

---

## 1. Host Infrastructure Specification

| Attribute | Specification |
| :--- | :--- |
| **Host Operating System** | Microsoft Windows 11 Home Single Language (64-bit, Build 26300) |
| **Host Processor (CPU)** | Intel(R) Core(TM) i9-14900HX (24 physical cores, 32 logical threads) |
| **Host System Memory (RAM)** | 23.64 GB Total Physical RAM (10.09 GB Available Headroom) |
| **Java Development Kit (JDK)**| OpenJDK 21.0.13+9-LTS (Eclipse Adoptium) / Microsoft OpenJDK 21.0.13.0.2 |
| **Gradle Build Tool** | Gradle 8.11.1 (Revision `481cb05a490e0ef9f8620f7873b83bd8a72e7c39`) |
| **Kotlin Compiler Version** | 2.0.20 |
| **Android Gradle Plugin (AGP)**| 8.10.1 |
| **Android SDK Path** | `C:\Users\pawan\AppData\Local\Android\Sdk` |
| **Build-Tools Version** | 35.0.0 (supporting `aapt2`, `apksigner`, `zipalign`) |
| **Local Network Interface** | Wi-Fi 6 (Realtek 8852BE-VT Wireless LAN WiFi 6 PCI-E NIC, 866.7 Mbps) |

---

## 2. Emulated Device Specification

| Attribute | Specification |
| :--- | :--- |
| **AVD Profile Name** | `Medium_Phone_API_37.0` |
| **Device Model / Brand** | `sdk_gphone64_x86_64` (Google Play system image) |
| **Android OS Version** | Android 17 (Developer Preview) |
| **Android API Level** | **API 37** (`ro.build.version.sdk=37`) |
| **CPU Architecture (ABI)** | `x86_64` (`ro.product.cpu.abi=x86_64`) |
| **Hardware Platform** | `ranchu` (QEMU-based hypervisor engine) |
| **Emulated RAM** | 4,007,632 kB (~4.0 GB) |
| **Display Resolution** | 1080 x 2400 pixels (420 dpi density) |
| **Graphics Mode** | SwiftShader indirect OpenGL ES 3.0 / Vulkan 1.2 host emulation |
| **Battery Service State** | Level: 100%, Temp: 25.0°C, Voltage: 5000 mV, Technology: Li-ion |

---

## 3. Physical Device Testing Limitation Disclosure

- **Hardware Status:** No physical Android hardware was connected over USB or wireless ADB at the time of testing (`List of devices attached` empty prior to emulator startup).
- **Protocol Adherence:** In accordance with section 2 of the audit instructions:
  > *"Do not treat emulator battery measurements as representative of physical-device battery consumption. If physical hardware is unavailable, clearly mark hardware-dependent tests as BLOCKED."*
- **Marked Tests:** Physical battery drain soak testing over a 24-hour continuous discharge cycle without external power is marked **BLOCKED (Hardware Unavailable)**, while all software-level energy profiling (CPU wakeups, thread loops, recompositions, and socket events) was fully executed and measured on the Android 17 emulator and JVM benchmark runners.

---

## 4. Evaluated Software Artifacts

| Artifact Name | Build Type | Version | Size (Bytes) | SHA-256 Digest |
| :--- | :--- | :--- | :--- | :--- |
| `ub-blocker-2.4.1.apk` | Release | 2.4.1 (30) | 5,156,259 | `4DA3CB84312B22DED38BF542D848EDD52CBE27404897CF429CB87ED0248B946B` |
| `ub-blocker-2.4.1.apk` | Debug | 2.4.1 (30) | 22,925,594 | `5D93DAA26132E739EF500FE8D7313684453ED187CE95CABC97E88FD1FD17CD16` |
