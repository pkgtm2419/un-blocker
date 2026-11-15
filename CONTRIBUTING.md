# Contributing to Un-Blocker

Un-Blocker is intended to become a community-maintained, privacy-first Android DNS blocker.
Contributions from Android developers, privacy researchers, DNS experts, testers, designers,
technical writers, and accessibility specialists are welcome.

> [!IMPORTANT]
> Read [AGENTS.md](AGENTS.md) and [PRIVACY.md](PRIVACY.md) in full before making any change.
> The privacy constraints below are **non-negotiable** and apply to every contribution.

---

## 1. Prerequisites & Setup

| Tool | Version | Notes |
| :--- | :--- | :--- |
| JDK | 17 | Set `JAVA_HOME` accordingly |
| Android SDK | 35 (API 35) | `compileSdk=35`, `targetSdk=35`, `minSdk=26` |
| Android Gradle Plugin | 8.10.1 | Managed by Gradle wrapper |
| Kotlin | 2.0.21 | Managed by version catalog |
| Python | 3.11+ | For tooling scripts; no third-party packages needed |

**Android SDK setup:** Install platform 35 and the matching build tools. Set `ANDROID_HOME`
or configure an untracked `local.properties` (never commit this file).

**Clone and verify the build:**

```bash
git clone https://github.com/pkgtm2419/un-blocker.git
cd un-blocker
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell: substitute `.\gradlew.bat` for `./gradlew` and `python` for `python3`.

---

## 2. Contribution Areas

Un-Blocker welcomes help in the following areas:

- **Filtering datasets** — expand deterministic, properly licensed filtering regression datasets.
- **DNS correctness** — improve DNS parser correctness and malformed-packet coverage.
- **Device compatibility** — test reboot and network transitions across Android manufacturers.
- **Performance** — reduce battery, memory, and CPU use with reproducible benchmarks.
- **Accessibility & UI** — improve accessibility, translations, onboarding, and status explanations.
- **On-device learning** — strengthen private local learning without uploading user data.
- **Privacy documentation** — document bypass cases and privacy boundaries clearly.
- **False-positive corpus** — reproduce and fix false positives with synthetic or consented test data.

[**Browse open issues**](https://github.com/pkgtm2419/un-blocker/issues) ·
[**Fork the repo**](https://github.com/pkgtm2419/un-blocker/fork) ·
[**Review pull requests**](https://github.com/pkgtm2419/un-blocker/pulls)

---

## 3. Privacy Rules (Non-Negotiable)

> [!CAUTION]
> Any PR that violates these rules will be rejected without further review.

- **Keep filtering and learning entirely on-device.** Do not add telemetry, analytics SDKs,
  remote model calls, cloud sync, or automatic account/subscription features.
- **Do not upload domain lists or browsing data.** DNS query names are sensitive; treat them
  as such throughout the codebase.
- **No plaintext domain persistence in learned-identifier stores.** Custom rules and learned
  domains use Keystore-backed HMAC-SHA256 identifiers. Local query-log aggregates and the
  "My domains" protection list may store plaintext; do not widen that scope.
- **No remote model inference.** `HeuristicDomainClassifier` runs on-device. Do not add
  network-backed ML calls.
- **No hidden resolver fallback.** The active VPN path must not fall back to hardcoded public
  DNS resolvers (Cloudflare, Google, Quad9, etc.) without an explicit product/security decision.
- **No private-data backups.** The app opts out of Android Auto-Backup for sensitive stores.
- Use invented or public test domains in reports and fixtures — never your personal DNS history.

---

## 4. Code Style

All Kotlin, Gradle (Kotlin DSL), and relevant XML changes must follow the project's
senior code-style contract defined in
[`.agents/skills/kotlin-senior-code-style/SKILL.md`](.agents/skills/kotlin-senior-code-style/SKILL.md).

Key rules at a glance:

- **Indentation:** 4 spaces, no tabs.
- **Line length:** 120 characters maximum.
- **Naming:** descriptive, compatible names; no abbreviations in public APIs.
- **KDoc:** accurate public KDoc on every public declaration; include `@param`, `@return`,
  `@throws` where meaningful.
- **Thread safety:** use `@MainThread` / `@WorkerThread` annotations; never block the main
  thread; prefer coroutines with explicit dispatchers.
- **Android lifecycle:** follow Activity/Service/ViewModel lifecycle contracts; hold no
  strong references across lifecycle boundaries.
- **Compose:** hoisted state, stable parameters, preview annotations; keep composable names
  compatible with Navigation Compose routes.
- **Formatter:** run the pinned formatter/checks before pushing; report any unavoidable
  compatibility exception in the PR description.

Do not initiate unrelated style rewrites in a PR focused on a bug or feature.

---

## 5. Testing Requirements

Every change must include tests that demonstrate the corrected or new behavior.

### Required before every PR

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Android lint
./gradlew lintDebug

# APK build smoke check
./gradlew assembleDebug

# Rule compiler and release policy unit tests
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v

# Script checks
bash scripts/tests/run-all.sh
```

### Required for VPN / Keystore / preferences changes

```bash
# Requires an isolated emulator or device with VPN permission granted
./gradlew connectedDebugAndroidTest
```

> [!NOTE]
> Run instrumentation tests on a **disposable emulator** (not your daily-use device).
> Record which Android API levels were actually tested (CI matrix covers API 26, 30, 35).
> Test real reboots separately from a synthetic boot broadcast — they behave differently on many OEMs.

### Documentation tooling checks (required when docs change)

```bash
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

Commit any resulting `docs/generated/` inventory changes in the same PR.

### Lint policy note

`app/build.gradle.kts` currently sets `abortOnError=false` and `ignoreWarnings=true` for
some lint detectors (audit finding R07). A passing lint step in CI does **not** mean zero
lint issues. Review lint output manually for new code paths.

### Verification record

Include a table like this in your PR description:

| Command | Environment | Result | Evidence / limitation |
| :--- | :--- | :--- | :--- |
| `./gradlew testDebugUnitTest` | JDK 17, SDK 35 | PASS / FAIL / NOT RUN | attach output or CI URL |
| `./gradlew lintDebug` | JDK 17, SDK 35 | PASS / FAIL / NOT RUN | note lint policy |
| `./gradlew assembleDebug` | JDK 17, SDK 35 | PASS / FAIL / NOT RUN | APK path/hash |
| `./gradlew connectedDebugAndroidTest` | emulator API level | PASS / FAIL / NOT RUN | consent, real network |
| Python / doc checks | Python 3.11+ | PASS / FAIL / NOT RUN | stdout |

---

## 6. Pull Request Process

> [!NOTE]
> ASSUMPTION: branch naming follows the standard convention below, inferred from project practice.

**Branch naming:**

| Prefix | Use for |
| :--- | :--- |
| `feature/<topic>` | New behavior or capability |
| `fix/<topic>` | Bug fix |
| `docs/<topic>` | Documentation-only change |

**Target branch:** always `main`.

**Required CI checks:**

| Check | What it validates |
| :--- | :--- |
| `build` | Compiler, policy, JVM tests, lint, APK assembly |
| `instrumentation` | Emulator privacy and VPN behavior |

**PR checklist:**

1. Read [AGENTS.md](AGENTS.md), [PRODUCT.md](PRODUCT.md), and affected module docs before starting.
2. Keep each PR to one bounded logical change.
3. Reproduce the bug or write a failing test *before* changing behavior.
4. Include positive, negative, malformed-input, race-condition, migration, and UX test cases
   where applicable.
5. In the same PR, update any directly affected human-owned docs:
   `docs/FEATURE_MATRIX.md`, feature/module docs, `docs/BEHAVIOR_CONTRACTS.md`,
   changelog, and roadmap when relevant.
6. Fill in the verification record table (§ Testing Requirements above).
7. Provide a PR summary: requirements, touched paths, observed/expected impact, test evidence,
   privacy/security assessment, migration concerns, docs touched, remaining uncertainties.
   **No fabricated metrics.**
8. Do not lower the `FP=0` checked-in corpus baseline to make a failing rule pass.
9. Explain dataset provenance and scope; corpus metrics are not global effectiveness numbers.

---

## 7. Dataset & Dependency Rules

- **Preserve third-party attribution.** Document the license and source of every added
  filter list, blocklist, seed dataset, or third-party dependency.
- **Only license-reviewed data.** Do not add proprietary, unlicensed, or personal-traffic
  datasets. StevenBlack lists ship under MIT with pinned provenance.
- **Treat downloaded rule lists as untrusted input.** Bounded parsing, response validation,
  redirect validation, and resource limits must survive any refactor to the fetch/parse path.
- **APK and keystore files must never enter source control.** Binaries belong in GitHub
  Releases only.

---

## 8. Release Policy

Only project maintainers publish matching version tags using the existing private signing key.

- Keep keystores and APK binaries out of source control.
- Published download links must point to actual release assets, not a CI run page or an
  unpublished APK.
- Do not repurpose the `release.yml` workflow for ordinary feature PRs.
- Do not change release signing policy or weaken GitHub Actions to make CI pass.
- Application source contributions are licensed under **Apache-2.0**.

---

*Help build an Android blocker whose users do not have to trade their browsing privacy for
protection.*
