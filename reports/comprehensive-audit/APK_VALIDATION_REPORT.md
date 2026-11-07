# Phase 5: APK Security & Binary Integrity Validation

**Artifact Evaluated:** `app/build/outputs/apk/release/ub-blocker-2.4.1.apk`  
**Evaluation Tools:** `aapt2`, `apksigner`, Python APK native library analyzer, Zip inspection.  
**Validation Date:** November 7, 2026  

---

## 1. Artifact Metadata & Core Attributes

| Attribute | Measured Value | Requirement / Baseline | Validation Status |
| :--- | :--- | :--- | :--- |
| **File Name** | `ub-blocker-2.4.1.apk` | Must match release policy convention | **VALID** |
| **File Size (Release)** | **5,156,275 bytes (~5.15 MB)** | < 15 MB | **EXCELLENT (Lightweight)** |
| **File Size (Debug)** | **22,925,594 bytes (~22.9 MB)** | Unminified baseline | **VALID** |
| **Package Name** | `com.unblocker.app` | `com.unblocker.app` | **VALID** |
| **Version Code** | `30` | Increment from 29 | **VALID** |
| **Version Name** | `"2.4.1"` | Increment from 2.4.0 | **VALID** |
| **minSdkVersion** | `26` (Android 8.0 Oreo) | API 26+ support | **VALID** |
| **targetSdkVersion** | `35` (Android 15) | Modern platform compliance | **VALID** |
| **compileSdkVersion**| `35` (Android 15) | Modern SDK tools | **VALID** |
| **Debuggable Flag** | `android:debuggable="false"` | Must NOT be debuggable in release | **VALID** |
| **Allow Backup** | `android:allowBackup="false"` | Must be false | **VALID** |
| **Uses Cleartext** | `android:usesCleartextTraffic="false"`| Must be false | **VALID** |

---

## 2. Signature & Integrity Verification (`apksigner`)

Verification executed via Android SDK Build Tools 35.0.0 `apksigner`:
```bash
$ apksigner.bat verify --verbose ub-blocker-2.4.1.apk
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
```

- **V2 Scheme Verification:** **TRUE**. The APK contains an intact APK Signing Block, guaranteeing cryptographic tamper-evidence over all ZIP entries before installation.
- **Certificates:** Local test build correctly signed with V2 signature scheme. CI release workflow applies production release keystore with SHA-256 fingerprint verification via `scripts/verify-release.sh`.

---

## 3. DEX Code Architecture & R8 Optimization

- **Total DEX Files in Release APK:** **1 (`classes.dex`)**
- **Multidex Overhead:** **Eliminated**. Thanks to R8 full-mode tree-shaking and dead code removal, the entire compiled application, Jetpack Compose runtime, Room, and Hilt graph fits neatly within a single 64k-method DEX file.
- **Obfuscation Status:** Active. Class and member names are minified; unused methods and reflection entry points are pruned.

---

## 4. Native Library Inventory (`lib/`)

Inspected using `scripts/check-release-native-libs.py`:
```
APK: ub-blocker-2.4.1.apk
Total entries: 134
Native .so count: 4
Model files count: 0
PASSED: No unverified native TFLite binaries found in APK.
```

### Full Architecture Coverage:
1. `lib/arm64-v8a/libandroidx.graphics.path.so` (64-bit ARM)
2. `lib/armeabi-v7a/libandroidx.graphics.path.so` (32-bit legacy ARM)
3. `lib/x86_64/libandroidx.graphics.path.so` (64-bit x86 emulators/tablets)
4. `lib/x86/libandroidx.graphics.path.so` (32-bit x86 emulators)

**Security Finding:** Zero unauthorized native `.so` binaries (e.g. unverified TFLite C++ runtimes or third-party ad SDK binaries). Only the official AndroidX Graphics Path rendering library is bundled.

---

## 5. Assets & Bundled Data Inventory (`assets/`)

| Asset File | Purpose & Verification |
| :--- | :--- |
| `assets/dns-rules.bin` | Pre-compiled binary FNV-1a Trie rule engine index. |
| `assets/public_suffix_list.dat` | Mozilla Public Suffix List for accurate registrable domain extraction. |
| `assets/dexopt/baseline.prof` | Compiled ART baseline profile for fast cold startup. |
| `assets/dexopt/baseline.profm` | ART baseline profile metadata. |
| `assets/ad_domains.txt` | Bundled curated fallback advertising domains. |
| `assets/adult_domains.txt` | Bundled curated adult-content filter domains. |
| `assets/never-block-hosts.txt` | Essential bootstrap and CDN domains never blocked. |
| `assets/licenses/*` | Complete open-source attribution, NOTICE, and LICENSE files. |

**Zero Sensitive Assets:** No private keys, `.pem` certificates, test credentials, or server URLs are stored in the asset catalog.

---

## 6. Manifest Permission Audit (`aapt2 dump badging`)

```
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
uses-permission: name='android.permission.FOREGROUND_SERVICE_SPECIAL_USE'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'
uses-permission: name='android.permission.WAKE_LOCK'
uses-permission: name='com.unblocker.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

- Every permission is directly required for local VPN networking, boot restoration, and foreground notification status.
- Zero privileged or dangerous permissions (camera, microphone, contacts, location, storage) are declared.
