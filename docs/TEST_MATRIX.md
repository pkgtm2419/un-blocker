# Change-to-test matrix

| Change touched | Must inspect/run | Extra gates |
| --- | --- | --- |
| `app/src/main/java/com/unblocker/app/logic/dns/`, `app/src/main/java/com/unblocker/app/services/*Dns*`, `UnblockerVpnService` | `app/src/main/java/com/unblocker/app/dns/*Test.kt`, `app/src/main/java/com/unblocker/app/services/*Dns*Test.kt`, `connectedDebugAndroidTest` | IPv4/IPv6, checksum, bad replies, TCP, no network loops |
| `domain/usecase`, `logic/AdDetector`, `logic/rules` | `DecideBlockingUseCaseTest`, `RuleSetDifferentialTest`, `RuleLineParserTest` | precedence + protected domain false positives |
| `logic/analysis`, `DeviceLearning`, private rules | `EvidenceLearningTest`, `PrivateEvidenceStoreTest`, `PrivateDomainSetTest`, migration instrumentation | no plaintext identifiers, user feedback compatible |
| `data-store/db`, `data/logs` | Room migration tests, `QueryLogSinkTest`, privacy regression | opt-in/clear/retention, no loss/reappearance |
| `data/repository/BoundedFilterDownloader`, extension manifest | downloader/repository/rollback tests, Python filter compiler tests | HTTPS + SSRF + caps + source license |
| `ui/screens`, `ui/settings`, `ui/logs`, `ui/learn`, `ui/extensions` | ViewModel/mapper tests and connected UI instrumentation | states, accessibility, permissions, screen sizes |
| `receivers`, `QuickStartManager`, `AndroidManifest` | lifecycle/manifest test + cold boot when possible | exported flags, FGS, OEM limits |
| `build.gradle.kts`, `gradle/`, `.github/workflows` | full JVM/lint/APK/tooling and release policy tests | source/version/signing/CI policy |
| `docs/`, `.agents/`, `scripts/docs_*` | `docs_contract`, `docs_sync --check`, docs tooling unittest | valid skill/rule frontmatter, links, drift |

**Commands:** see [BUILD_AND_TEST.md](BUILD_AND_TEST.md). **Rule:** test evidence is execution-specific; existence of a file is not a pass.
