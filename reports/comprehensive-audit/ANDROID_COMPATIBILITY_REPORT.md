# Phase 6: Android Platform Compatibility Report

**Project:** Un-Blocker (`com.unblocker.app`)  
**Target Release:** `v2.4.1` (versionCode `30`)  
**Minimum Supported Version:** Android 8.0 Oreo (API 26)  
**Target Platform Version:** Android 15 (API 35)  
**Tested Live Platform:** Android 17 (API 37 Preview, `sdk_gphone64_x86_64`)  
**Date:** November 7, 2026  

---

## 1. Android API Level Compatibility Matrix

| Android Version | API Level | Compatibility Status | Platform Adaptation & Verification Details |
| :--- | :--- | :--- | :--- |
| **Android 8.0 – 8.1** | API 26 – 27 | **SUPPORTED** | Baseline target. Notification channels configured; VpnService basic builder APIs. |
| **Android 9.0** | API 28 | **SUPPORTED** | Private DNS (DoT) detection logic active; foreground service permission declared. |
| **Android 10** | API 29 | **SUPPORTED** | Scoped storage compliant (no external shared storage access needed); Dark theme integration. |
| **Android 11** | API 30 | **SUPPORTED** | Package visibility `<queries>` declared in manifest for installed app discovery in Learn tab. |
| **Android 12 – 12L** | API 31 – 32 | **SUPPORTED** | `PendingIntent.FLAG_IMMUTABLE` strictly enforced across all notification actions. |
| **Android 13** | API 33 | **SUPPORTED** | `POST_NOTIFICATIONS` runtime permission requested at protection start; verified on live emulator. |
| **Android 14** | API 34 | **SUPPORTED** | `android:foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declared. |
| **Android 15** | API 35 | **SUPPORTED** | 16 KB page alignment compliant; edge-to-edge window insets supported. |
| **Android 16 – 17** | API 36 – 37 | **VERIFIED LIVE** | Live emulator validation passed: installed, launched, filtered, and torn down with 0 errors. |

---

## 2. Platform-Specific Architectural Adaptations

### 2.1 Android 14+ Foreground Service Types (`specialUse`)
Under Android 14+ (API 34+), foreground services must declare a specific `foregroundServiceType`. Starting an un-typed service throws a fatal `ForegroundServiceTypeNotFoundException`.
- **Manifest Configuration:**
  ```xml
  <service
      android:name="com.unblocker.app.services.UnblockerVpnService"
      android:exported="false"
      android:foregroundServiceType="specialUse"
      android:permission="android.permission.BIND_VPN_SERVICE">
      <property
          android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
          android:value="Local on-device DNS blocking for unwanted ad and adult content network traffic" />
  </service>
  ```
- **Validation:** Verified on Android 17 emulator; the service starts cleanly and displays a persistent notification without system termination.

### 2.2 Android 13+ Notification Runtime Permissions
- On Android 13+ (API 33+), notifications require runtime user consent (`Manifest.permission.POST_NOTIFICATIONS`).
- In `UnblockerScreen.kt`, tapping "Enable Blocker" prompts the user with the system notification dialog prior to starting the foreground service. If denied, the app degrades gracefully, maintaining filtering while notifying the user of reduced background persistence.

### 2.3 Android 11+ Package Visibility (`<queries>`)
- To support the "Learn an App" feature without violating Google Play's high-risk `QUERY_ALL_PACKAGES` policy, the manifest declares an intent-based query:
  ```xml
  <queries>
      <intent>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent>
  </queries>
  ```
- This allows the app to enumerate user-launchable apps (e.g. Chrome, YouTube, Games) while remaining strictly compliant with Google Play target privacy requirements.

### 2.4 Android 9+ Private DNS (DoT) Coexistence
- When Android's system "Private DNS" is set to "Strict / Cloudflare / Google", Android forces DNS over TLS (DoT) on port 853, bypassing standard UDP port 53.
- `un-blocker` actively inspects system network link properties (`LinkProperties.isPrivateDnsActive`). The dashboard accurately reports the status as `Protected (standard DNS)` or displays an educational prompt explaining that Private DNS overrides local port-53 blocking when configured in strict mode.

---

## 3. Screen Form Factors & Orientation

- **Portrait & Landscape:** Handled via Compose responsive layouts and `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"`. Rotating the device retains scroll position and filter selections without recreating the ViewModel or resetting the active VPN tunnel.
- **Window Size Classes:**
  - Compact (Standard Phone): Verified on 1080x2400 emulator.
  - Expanded (Tablets / Foldables): Compose layout accommodates wider aspect ratios with centered cards and scrollable lazy columns.
