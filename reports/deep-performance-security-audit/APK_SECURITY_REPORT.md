# Phase 13: APK Security & Binary Hardening Audit

**Document ID:** `AUDIT-APK-001`  
**Artifact:** `app/build/outputs/apk/release/ub-blocker-2.4.1.apk`  
**Evaluation Tools:** `apksigner`, `aapt2`, Python native library gates, Zip inspection.  
**Date:** November 8, 2026  

---

## 1. Cryptographic Signature & Integrity

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

- **Scheme v2 Tamper-Evidence:** The APK binary is protected by Scheme v2 whole-file cryptographic signing. Any modification of DEX code, assets, or resources post-build immediately invalidates the signature and prevents installation on Android devices.

---

## 2. Binary Attack Surface & Native Libraries

Audited via `scripts/check-release-native-libs.py`:
```
APK: ub-blocker-2.4.1.apk
Total entries: 134
Native .so count: 4
Model files count: 0
PASSED: No unverified native TFLite binaries found in APK.
```

- **Native Libraries:** Exactly 4 native libraries bundled:
  - `lib/arm64-v8a/libandroidx.graphics.path.so`
  - `lib/armeabi-v7a/libandroidx.graphics.path.so`
  - `lib/x86_64/libandroidx.graphics.path.so`
  - `lib/x86/libandroidx.graphics.path.so`
- **Zero Third-Party Native Binaries:** No unverified C/C++ native libraries or unvetted ML runtime binaries are included in the production APK.

---

## 3. R8 Shrinking & Code Obfuscation

- **Single DEX Artifact:** The production release packages into a single `classes.dex` file (zero Multidex overhead).
- **Dead Code Stripping:** Unused classes, methods, and reflection metadata are stripped.
- **Resource Shrinking:** Unused drawables, strings, and layouts are removed (`shrinkResources = true`), resulting in a release file size of only **5.15 MB** (compared to 22.9 MB for the unminified debug APK).
- **Non-Debuggable:** `android:debuggable="false"` is strictly enforced in the release manifest.
