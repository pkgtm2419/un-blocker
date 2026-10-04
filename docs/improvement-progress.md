# Un-Blocker Improvement Progress & Verification Tracker

## 1. Overview
This document tracks the end-to-end implementation and verification of the architectural improvements specified in `docs/archive/ANTIGRAVITY_UNBLOCKER_SPEC.md` and subsequent security and autonomous on-device enhancements up to release **1.4.1** (`versionCode 8`).

---

## 2. Workstream Status

| Workstream | Objective | Status | Key Deliverables & Changes |
| :--- | :--- | :--- | :--- |
| **PR-1 (S-1..S-4, Q-1)** | Security Hardening & CI Integrity | **Complete** | Hardened `.github/workflows/release.yml`, removed untracked binaries, pinned GitHub Actions SHAs (`android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd`), added `.github/dependabot.yml`, and committed `gradle/verification-metadata.xml`. |
| **PR-2 (A.4..A.6, C-5)** | Apex Domain Safety & Lexical Scoping | **Complete** | Added `NeverBlockPolicy` and `never_block.txt` protecting critical infrastructure and vendor apex domains; scoped lexical analysis exclusively to registrant-controlled subdomains; constrained adult content regexes to prevent false positives. |
| **PR-3 (S-5, R-3, R-4)** | CNAME Poisoning & Protocol Robustness | **Complete** | Fixed persistent CNAME cache poisoning in `ContentFilterEngine.kt` by evaluating aliases via policy; implemented standards-compliant `FORMERR` and `NOTIMP` DNS packets in `DnsPacket.kt`; safeguarded `FilteringPreferences` against empty allowlists/blocklists. |
| **PR-4 (R-2)** | Datapath Latency & Lock-Free Classification | **Complete** | Converted `UnblockerVpnService.kt` to lock-free classification on the DNS forwarding fast path; introduced asynchronous write-behind persistence in `PrivateEvidenceStore.kt`. |
| **PR-5 (A.1..A.3, R-5)** | Binary Rule Engine & Vendored StevenBlack List | **Complete** | Vendored `StevenBlack/hosts` (72,233 domains, MIT) at pinned commit `21605cc`; developed little-endian binary `UBR2` format (`dns-rules.bin`, ~2.0 MB) with reversed-label binary search; implemented `RuleSet.kt` and shared zero-allocation `RuleSetHolder.kt`. |
| **PR-6 (R-1)** | TCP DNS Fallback Responder | **Complete** | Implemented user-space non-blocking TCP DNS server in `TcpDnsResponder.kt` bound to `10.10.0.1:53` and `[fd00:1::1]:53`, addressing RFC 7766 and large responses. |
| **PR-7 (A.7)** | Private DNS (DoT) Warning & Detection | **Complete** | Added real-time Android Private DNS (DoT) detection and in-app warning banner in `UnblockerScreen.kt` informing users when encrypted Android DNS bypasses local VPN filtering. |
| **PR-8 (Q-2, §6, §7)** | Corpus Evaluation & Performance Baseline | **Complete** | Cleaned build dependencies (removed unused KSP); added `tools/eval/har_hosts.py`, `CoverageReportTest.kt`, `labeled-hosts.csv`, and `docs/coverage-baseline.md`. |
| **v1.4.1 Security & On-Device Autonomy** | Autonomous Ad Analysis & Safety Guard | **Complete** | Created `NeverBlockPolicy.kt` protecting OS captive portal, NTP, DNS, and critical infrastructure; integrated on-device ad heuristic reason labeling in `LocalNetworkLearner.kt` and `HeuristicScorer.kt`; bounds-checked domain lengths; added `OnDeviceAdAnalysisTest.kt`. |

---

## 3. Verification Suite
All test suites pass deterministically offline:
- **JVM Unit Tests**: 204 tests passing (0 failures, 0 skips) via `JAVA_HOME=/usr/lib/jvm/java-17 ./gradlew testDebugUnitTest`.
- **Android Lint**: 0 errors via `JAVA_HOME=/usr/lib/jvm/java-17 ./gradlew lintDebug`.
- **Compiler & Release Policy Tests**: 13 tests passing via `python3 -m unittest test_compiler test_release`.
- **HAR Host Evaluation**: 2 tests passing via `python3 -m unittest tools/eval/test_har_hosts.py`.
- **Binary Check**: Verified 0 tracked keystore/apk binaries via `scripts/check-no-binaries.sh`.
