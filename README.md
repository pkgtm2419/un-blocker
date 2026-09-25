# 🛡️ unblocker (unwanted network blocker)

<div align="center">

<img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" height="120" alt="unblocker Logo"/>

### **Zero-Server, On-Device Network Traffic Shield for Android**

*Block unwanted advertisements, telemetry, trackers, and 18+ adult content locally with zero latency and zero privacy compromises.*

<br/>

[![Download APK](https://custom-icon-badges.demolab.com/badge/DOWNLOAD%20APK-v1.0.0%20(DIRECT%20DOWNLOAD)-0D9488?style=for-the-badge&logo=download&logoColor=white)](https://github.com/pkgtm2419/un-blocker/raw/main/release/unblocker-v1.0.0.apk)

<br/>

👉 **[⬇️ Direct Download `unblocker-v1.0.0.apk` (17.4 MB)](https://github.com/pkgtm2419/un-blocker/raw/main/release/unblocker-v1.0.0.apk)** 👈

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-100%25%20On--Device-10B981?style=flat-square)](#architecture)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)

</div>

---

## 🚀 Download & Installation

Click the download button above or use the direct link below:

* **[Download unblocker APK v1.0.0](https://github.com/pkgtm2419/un-blocker/raw/main/release/unblocker-v1.0.0.apk)**

### Installation Steps:
1. Download `unblocker-v1.0.0.apk` on your Android device.
2. Open the downloaded file and tap **Install** (allow installation from unknown sources if prompted).
3. Open **unblocker** and tap the giant **"START BLOCKING"** button.
4. Accept the Android VPN connection request.
5. Your entire device network traffic is now protected!

---

## ✨ Features

### 1. ⚡ Quick Start (One-Button Launch)
- Center-stage tactile **"START BLOCKING"** button on the splash landing screen.
- Automatically initializes databases, checks permissions, and starts background services.
- Seamlessly transitions to **"SERVICES ACTIVE"** with real-time status indication.
- Low-priority ongoing status notification showing live counts of blocked ads & adult content.

### 2. 🛑 Dual-Layer Local Blocking (Zero Server)
- **Ad & Tracker Blocker**:
  - Over **12,000+** embedded known ad networks, trackers, and telemetry domains.
  - Multi-pattern regex engine (`*.ads.*`, `*.adservice.*`, `*.doubleclick.net`, `*.admob.*`, `*.applovin.*`, `*.unityads.*`, `*.criteo.*`, `*.taboola.*`, etc.).
- **18+ Adult Content Blocker (Independent Toggle)**:
  - Over **4,200+** pre-compiled adult domains.
  - TLD filtering rules (`.xxx`, `.adult`, `.porn`, `.sex`, `.cam`).
  - Regex pattern matching with built-in false-positive safeguards for educational and public domains.
  - Can be toggled independently from ad blocking at any time.

### 3. 🔍 DNS Spoofing Engine (`0.0.0.0` in < 1ms)
- Intercepts outgoing UDP port 53 DNS queries via Android `VpnService` TUN interface.
- Blocked queries are answered locally with `0.0.0.0` (or `::`) in under **1 millisecond** with **zero data consumed**.
- Permitted queries are forwarded to secure upstream DNS (`1.1.1.1` / `8.8.8.8`) via protected sockets.

### 4. 📱 Responsive Across Every Device Screen
- Built entirely with **Jetpack Compose Material 3**:
  - **Phones (Compact)**: Bottom navigation bar, vertical scroll flow, responsive cards.
  - **Foldables & Landscape (Medium)**: Side-docked `NavigationRail`, 2-column card grid.
  - **Tablets & Desktop/Chromebooks (Expanded)**: Side-docked `NavigationRail` with header, 4-column metric grid, side-by-side Top Blocked comparisons.

### 5. 📊 Real-Time Logs & Analytics
- Live connection audit trail with filters (`[All]`, `[Ads Only]`, `[18+ Adult]`, `[Blocked]`, `[Allowed]`).
- Modal bottom sheet with domain, app name, protocol, port, detection rule, and confidence.
- Export logs as **CSV** or clear log database on demand.
- Visual traffic distribution bar and top blocked domains breakdown.

### 6. 🩺 Self-Checking & Diagnostics
- Periodically verifies VPN service health and database integrity (`PRAGMA integrity_check`).
- Dynamic effectiveness benchmark testing against known ad/adult domains to calculate real-time score.
- Auto-restarts on boot (`RECEIVE_BOOT_COMPLETED`).

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose & Material Design 3
- **Networking**: Android `VpnService` with custom zero-dependency DNS packet parser/synthesizer
- **Database**: AndroidX Room 2.6.1 (SQLite) with multi-index queries
- **Background Tasks**: AndroidX WorkManager 2.9.1
- **Target SDK**: Android 15 (API 35) | **Min SDK**: Android 8.0 (API 26)

---

## 📦 Building from Source

```bash
# Clone the repository
git clone https://github.com/pkgtm2419/un-blocker.git
cd un-blocker

# Build the Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest
```

The compiled APK will be output at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy Guarantee

**unblocker** is strictly zero-server:
- All domain databases, pattern matchers, and rule engines operate locally on your device.
- No analytics, logs, telemetry, or connection data are ever sent to any remote server.
- All logs remain in your device's private SQLite database and can be cleared at any time.
