# Un-Blocker — workspace instructions for Antigravity and coding agents

> Applies to the entire `pkgtm2419/un-blocker` repository. Read this **before** making code, build, rules, privacy, UI, or release changes. Source snapshot for this documentation: `main` at `8934519b021d56f12e90d824c20b4f60be1bda4b` (2026-11-10). Re-check against your current checkout.

## Mission and source of truth

**Every prompt:** read and apply [kotlin-senior-code-style](.agents/skills/kotlin-senior-code-style/SKILL.md). This workspace-wide requirement covers Kotlin, relevant XML and Gradle work, including review. Preserve public APIs and behavior; apply style to the task's code without initiating unrelated rewrites.

Un-Blocker is a local-first Android DNS blocker. It uses the Android `VpnService` to inspect intercepted DNS traffic, not application HTTPS bodies, and evaluates advertising, tracking and optional adult-domain rules on-device. Its product boundaries are intentional. Read [PRODUCT.md](PRODUCT.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md), and [docs/BEHAVIOR_CONTRACTS.md](docs/BEHAVIOR_CONTRACTS.md).

**Evidence order:** (1) checked-out implementation, wiring and build scripts; (2) passing tests; (3) checked-in configuration/assets; (4) maintained project contracts in this pack; (5) legacy README, comments and `.gsd` milestone files. When these disagree, **investigate; never silently pick the more flattering claim**. Record findings in [docs/REPO_RECONCILIATION.md](docs/REPO_RECONCILIATION.md).

## Absolute constraints

1. Preserve on-device privacy: no account system, ads/analytics SDK, external inference, telemetry, browsing-data upload, private DNS-history logs to Logcat, or raw-domain learned-identifier storage without approved product/security changes.
2. The application is a **DNS filter, not an anonymity VPN**. Do not broaden routing, intercept TLS, install user CAs, or claim complete coverage of Private DNS/DoH/direct-IP flows.
3. Preserve user consent and Android VPN/foreground-service lifecycle, correct network selection, protected upstream sockets, and appropriate IPv4/IPv6, UDP/TCP behavior.
4. Treat DNS wire bytes and downloaded rule lists as untrusted; bounded parsing, response validation, redirect validation, resource limits and fail-safe behavior must survive refactors.
5. Never change allow/block precedence, `APPROVED_ONLY` behavior, protected domain exceptions, or engine error behavior as a side effect. First capture existing tests, then get explicit feature approval for behavioral changes.
6. Protect persisted data: Keystore-backed HMAC identifiers for allow/block rules and learning, non-destructive Room schema migrations, no private-data backup. **Local query-log aggregates and the separate My domains protection list can contain plaintext domains**; don't claim all stored data is hashed.
7. Do not add public DNS fallback to the **active** app VPN resolver path without an explicit product/security decision. `:vpn-engine` contains public resolver defaults; verify actual use before asserting runtime behavior.
8. Never add credentials, signing files, proprietary binaries, real browsing logs, invented benchmarks, hardcoded user domains, or unlicensed list content.
9. Do not introduce unapproved network or ML services, relax security checks, disable tests, weaken GitHub Actions, or change release signing policy to make CI green.
10. Keep each change minimal, backward-compatible, reviewed and testable. Use a feature branch and PR; never claim a passing test or release without execution evidence.

## Where to work

- Runtime VPN: `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`; filtering: `app/src/main/java/com/unblocker/app/logic/ContentFilterEngine.kt` and `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt`.
- DNS wire code: `app/src/main/java/com/unblocker/app/logic/dns/`, plus separately `vpn-engine/src/main/java/com/unblocker/vpn/`.
- Rules and list downloads: `app/src/main/java/com/unblocker/app/logic/rules/`, `app/src/main/java/com/unblocker/app/data/repository/`, `tools/filter-compiler/`, `app/src/main/assets/extensions/`.
- Device learning/user rules: `app/src/main/java/com/unblocker/app/logic/analysis/` and `app/src/main/java/com/unblocker/app/data/repository/`.
- Local logs/DB: `app/src/main/java/com/unblocker/app/data/logs/`, `data-store/src/main/java/com/unblocker/data/`.
- Compose UI: `app/src/main/java/com/unblocker/app/ui/` with `MainScreen.kt`; boot/health: `app/src/main/java/com/unblocker/app/receivers/`, `app/src/main/java/com/unblocker/app/services/`.
- Other Gradle modules: `:common`, `:data-store`, `:vpn-engine`, `:ml-engine`; do not assume all contained code is on the active data path.

## Standard work cycle

1. Identify request, user-visible acceptance cases, affected modules, security/privacy impact, and **verified** existing behavior. For non-trivial work write a plan in `docs/superpowers/plans/YYYY-MM-DD-topic.md`.
2. Search code and tests. Reproduce a bug or create a failing regression test *before* changing behavior. Inspect relevant `.agents/rules/` and use relevant `.agents/skills/` (especially `/feature-implementation`, `/dns-protocol-change`, `/privacy-security-review`).
3. Implement one bounded logical change; include positive, negative, malformed, race, migration and UX cases where appropriate. Preserve boundaries and data compatibility.
4. Run appropriate JVM, compiler, tooling, lint, APK and emulator tests; record exact commands, outputs, and any skipped checks. Never claim emulator tests passed when none ran.
5. In the **same PR** update the directly affected human-owned docs: `docs/FEATURE_MATRIX.md`, feature/module docs, product/architecture/privacy/security docs, behavior contracts, changelog/roadmap when relevant. Update legacy `README.md`, `PRIVACY.md`, `DATASETS.md` only if actually changing those original files.
6. Run `python3 scripts/docs_sync.py --write`, `python3 scripts/docs_contract.py`, `python3 scripts/docs_sync.py --check`, and `python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v`. Commit resulting `docs/generated/` inventory changes.
7. Produce a PR summary: requirements, touched paths, observed/expected impact, test evidence, privacy/security assessment, migration concerns, docs touched, remaining uncertainties. No fabricated metrics.

## Essential commands

Linux/macOS: `./gradlew testDebugUnitTest lintDebug assembleDebug`; Android device: `./gradlew connectedDebugAndroidTest`; Python tooling: `python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v`; script checks: `bash scripts/tests/run-all.sh`. Windows use `gradlew.bat` and `python` as appropriate. Project build facts live in [docs/BUILD_AND_TEST.md](docs/BUILD_AND_TEST.md). Production signing/release instructions are in [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md).

## Agent skill directory

`/kotlin-senior-code-style`, `/docs-maintainer`, `/feature-implementation`, `/dns-protocol-change`, `/privacy-security-review`, `/filter-list-update`, `/android-testing`, `/compose-ui-change`, `/bug-investigation`, `/code-review`, `/release-verifier` are stored under `.agents/skills/`. Prefer these modern skills over legacy `.agents/workflows`.

**Do not confuse automatic inventory generation with autonomous product truth.** CI can detect and propose Markdown updates, but feature semantics, privacy claims and new functionality require code-grounded review and a PR, not a blind rewrite.
