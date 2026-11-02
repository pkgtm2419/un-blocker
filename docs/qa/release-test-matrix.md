# Release Quality Gates and Test Matrix

## 1. Summary of Required Quality Gates

| Gate | Scope | Command / Tool | Pass Criteria |
|---|---|---|---|
| **JVM Unit Tests** | All modules (`:app`, `:common`, `:data-store`, `:ml-engine`, `:vpn-engine`) | `./gradlew testDebugUnitTest` | 100% pass (277/277 tests) |
| **Android Lint** | Code quality, resource leaks, SDK compliance | `./gradlew lintDebug` | Zero critical errors, zero `abortOnError` suppressions |
| **Release Signing Gate** | Tag and secret validation | `scripts/release-policy.py` | Fail-closed on missing production secrets for `v*` tags |
| **Native Library & 16 KiB Alignment** | APK binary inspection | `scripts/check-release-native-libs.py` | No unverified 4 KiB ELF binaries; pure Kotlin heuristic classifier |
| **Room Schema Migrations** | Database forward compatibility | `UnblockerMigrationsTest.kt` | Deterministic `1 -> 2` migration; no destructive fallback |
| **VPN DNS Split Tunnel** | Network routing correctness | `VpnRoutePolicyTest.kt` | Only synthetic endpoints `/32` and `/128`; zero public DNS capture |
| **Bounded Storage & Downloads** | Memory and network bounds | `BoundedFilterDownloaderTest.kt` | Max 16 MiB, max 4096B line, max 250k rules, HTTPS only |

---

## 2. Release Acceptance Protocol

1. **Clean Working Tree:** All changes committed with conventional commits (`type(scope): description`).
2. **Build and Test Verification:**
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleReleaseTest
   ```
3. **Native Gate Verification:**
   ```bash
   python scripts/check-release-native-libs.py app/build/outputs/apk/releaseTest/ub-blocker-2.2.0.apk
   ```
4. **Policy Unit Tests:**
   ```bash
   python -m unittest discover -s scripts -p 'test_*.py'
   ```
5. **No Production Publication Without Signed Evidence:** Production artifacts must strictly correspond to pinned SHA-256 certificate fingerprints.
