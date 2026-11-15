# AI Agent Guide — Un-Blocker Android Project

> **This is the single orientation document for AI development agents.** Read it before touching any code, docs, build scripts or rules. It consolidates `AGENTS.md`, `docs/AGENT_WORKFLOW.md`, `docs/QUALITY_GATES.md`, and `docs/DOCUMENTATION_MAINTENANCE.md`.
>
> Source snapshot: `main` at `8934519b021d56f12e90d824c20b4f60be1bda4b` (2026-11-10). Verify against your checkout.

---

## Table of Contents

1. [Quick Start for Agents](#1-quick-start-for-agents)
2. [Project Identity](#2-project-identity)
3. [Absolute Constraints](#3-absolute-constraints)
4. [How to Work in This Project](#4-how-to-work-in-this-project)
5. [Codebase Navigation](#5-codebase-navigation)
6. [How to Add a Feature](#6-how-to-add-a-feature)
7. [How to Write Tests](#7-how-to-write-tests)
8. [Definition of Done — PR Checklist](#8-definition-of-done--pr-checklist)
9. [What Not to Touch / Pitfalls](#9-what-not-to-touch--pitfalls)
10. [Available Agent Skills](#10-available-agent-skills)
11. [Documentation Rules](#11-documentation-rules)

---

## 1. Quick Start for Agents

### What to Read First (in order)

| Priority | File | Why |
|---|---|---|
| 1 | `AGENTS.md` | Workspace-level rules, constraints, skill registry |
| 2 | `PRODUCT.md` | What the product is and is NOT; hard limits |
| 3 | `docs/ARCHITECTURE.md` | Runtime data flow, Gradle module graph, lifecycle |
| 4 | `docs/BEHAVIOR_CONTRACTS.md` | Exact decision precedence in `DecideBlockingUseCase` |
| 5 | `docs/FEATURE_MATRIX.md` | Feature inventory with evidence and test anchors |
| 6 | Targeted `docs/features/<topic>.md` | Only for the specific area you are changing |

Do **not** load the entire `docs/` tree. Load only what is relevant to your task.

### How to Orient in the Codebase

```text
Task touches DNS handling?
  → docs/DNS_PIPELINE.md, app/src/main/java/com/unblocker/app/logic/dns/

Task touches UI/Compose?
  → app/src/main/java/com/unblocker/app/ui/, docs/UI_UX.md

Task touches rules/blocklists?
  → app/src/main/java/com/unblocker/app/logic/rules/, tools/filter-compiler/

Task touches privacy/data storage?
  → app/src/main/java/com/unblocker/app/data/logs/, data-store/, docs/PRIVACY_DATA_FLOW.md

Task touches the VPN service?
  → app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt
```

### Context Loading Strategy

- **Start minimal.** Load `AGENTS.md` + `PRODUCT.md` first; add docs lazily as the task demands.
- **Follow the code, not the docs.** Documentation is a navigation map; actual source and passing tests are the evidence of behavior.
- **Never load all of `docs/` up front.** The generated inventory under `docs/generated/` is auto-produced by scripts — do not read it as authoritative product truth.
- **Evidence order (most → least authoritative):**
  1. Checked-out implementation, wiring, build scripts
  2. Passing tests
  3. Checked-in configuration/assets
  4. Maintained project contracts (`PRODUCT.md`, `BEHAVIOR_CONTRACTS.md`, this guide)
  5. Legacy `README.md`, comments, `.gsd` milestone files

---

## 2. Project Identity

### What Un-Blocker IS

- A **local-first Android DNS blocker** that runs entirely on-device.
- Uses the Android `VpnService` API to intercept DNS UDP/TCP packets traversing a local TUN interface.
- Evaluates advertising, tracking, and optional adult-domain rules **on-device** — no cloud inference.
- Gives users transparency about why each domain was allowed or blocked.
- Supports `SMART` and `APPROVED_ONLY` policy modes, `BASIC`/`OPTIMAL`/`COMPLETE` filtering tiers.
- Provides extension lists (HTTPS-downloaded), private allow/block rules, and local heuristic learning.
- Stores local query-log aggregates and optional raw event history in Room (plaintext domains may be stored).

### What Un-Blocker is NOT

| Claim | Reality |
|---|---|
| Anonymity VPN | ❌ It is a DNS filter, not traffic anonymizer |
| TLS MITM proxy | ❌ It does not intercept HTTPS content |
| Blocking DoH / DoT / direct-IP | ❌ App-defined DoH, Private DNS, and direct-IP bypass the filter |
| Cloud AI / remote inference | ❌ All classification is local and deterministic |
| Account / analytics system | ❌ No account, no telemetry, no analytics SDK |
| Zero-false-positive guarantee | ❌ Measured effectiveness is bounded; never invent percentages |
| Complete coverage | ❌ OEM restrictions, Private DNS, and Direct-IP flows can bypass |

### Core Privacy Promise

- **No data leaves the device** without explicit user action (e.g., SAF CSV export).
- HMAC (Keystore-backed) identifiers protect learned allow/block rule identities.
- **Important nuance:** Local query-log aggregates and the "My domains" protection list store **plaintext domain names** in private app storage. Do not claim all stored data is hashed.
- No backup of private data; backup exclusions are manifest-declared.

---

## 3. Absolute Constraints

These ten rules are **non-negotiable**. Violating any of them requires an explicit product/security decision and a reviewed PR.

| # | Constraint | Why It Matters |
|---|---|---|
| 1 | **Preserve on-device privacy.** No account system, ads/analytics SDK, external inference, telemetry, browsing-data upload, private DNS-history to Logcat, or raw-domain learned-identifier storage without approved changes. | Core privacy promise; user trust |
| 2 | **DNS filter only, not an anonymity VPN.** Do not broaden routing, intercept TLS, install user CAs, or claim complete coverage of Private DNS/DoH/direct-IP flows. | Product boundary; prevents mis-selling security |
| 3 | **Preserve user consent and Android lifecycle.** VPN/foreground-service lifecycle, correct network selection, protected upstream sockets, appropriate IPv4/IPv6, UDP/TCP behavior must be maintained. | Android correctness; user safety |
| 4 | **Treat DNS wire bytes and downloaded rule lists as untrusted.** Bounded parsing, response validation, redirect validation, resource limits, and fail-safe behavior must survive refactors. | Security; malformed packet and SSRF protection |
| 5 | **Never change allow/block precedence, `APPROVED_ONLY` behavior, protected domain exceptions, or engine error behavior as a side effect.** First capture existing tests, then get explicit feature approval for behavioral changes. | Correctness; user expectations |
| 6 | **Protect persisted data.** Keystore-backed HMAC identifiers for allow/block rules and learning, non-destructive Room schema migrations, no private-data backup. Local query-log aggregates and "My domains" list **can contain plaintext domains** — don't claim all stored data is hashed. | Privacy accuracy; migration safety |
| 7 | **Do not add public DNS fallback to the active VPN resolver path** without an explicit product/security decision. `:vpn-engine` contains public resolver defaults; verify actual use before asserting runtime behavior. | Security; privacy |
| 8 | **Never add credentials, signing files, proprietary binaries, real browsing logs, invented benchmarks, hardcoded user domains, or unlicensed list content.** | Legal and security compliance |
| 9 | **Do not introduce unapproved network or ML services, relax security checks, disable tests, weaken GitHub Actions, or change release signing policy** to make CI green. | CI integrity; security |
| 10 | **Keep each change minimal, backward-compatible, reviewed and testable.** Use a feature branch and PR; never claim a passing test or release without execution evidence. | Quality; traceability |

---

## 4. How to Work in This Project

### Standard Workflow

```
Investigate → Plan → Tests → Implement → Review → Docs → Verify → PR
```

**Step-by-step:**

- [ ] **1. Investigate** — Identify the request, user-visible acceptance cases, affected modules, security/privacy impact, and **verified** existing behavior. Trace actual runtime callers (not just class names). Confirm repo/ref and uncommitted state.
- [ ] **2. Plan** — For non-trivial work, write a plan at `docs/superpowers/plans/YYYY-MM-DD-topic.md` with exact affected paths and per-step tests. Write acceptance criteria and a minimal threat model.
- [ ] **3. Failing test first** — Create a failing regression test (or reproduce a bug) *before* changing any behavior. Preserve current observable behavior unless the user explicitly authorized a change.
- [ ] **4. Implement** — Make one bounded logical change. Respect security, data compatibility, Android lifecycle, and privacy policy. Do not rewrite unrelated code.
- [ ] **5. Test** — Run the full applicable test matrix (see §7). Record exact commands, outputs, and gaps. Treat unknown results as `NOT RUN` — never claim tests passed without execution evidence.
- [ ] **6. Update docs** — In the **same PR**, update all directly affected human-owned docs: `docs/FEATURE_MATRIX.md`, the relevant `docs/features/<topic>.md`, architecture/privacy/security docs, behavior contracts, `CHANGELOG.md` (see §11).
- [ ] **7. Verify docs tooling** — Run the four required doc commands (see §11). Commit `docs/generated/` changes.
- [ ] **8. PR summary** — Include: requirements, touched paths, observed/expected impact, test evidence, privacy/security assessment, migration concerns, docs touched, and remaining uncertainties. **No fabricated metrics.**

### Where to Put Plans

```
docs/superpowers/plans/YYYY-MM-DD-topic.md
```

Example: `docs/superpowers/plans/2026-11-15-adult-filter-toggle.md`

### How to Handle Ambiguity

When code, docs, tests, or generated inventory **disagree**:

1. **Stop** — do not silently pick the more flattering claim.
2. **Investigate** — trace actual callers, check Hilt bindings, run a targeted test.
3. **Record** — open/update an issue in `docs/REPO_RECONCILIATION.md` with what is source-observed vs. documented.
4. **Mark** — add a test to decide the claim; do not rewrite semantics based on a script's output.

> Auto-generation never invents application behavior. CI can detect and propose Markdown updates, but feature semantics, privacy claims, and new functionality require code-grounded review.

---

## 5. Codebase Navigation

### Gradle Module Map

| Module | Purpose | Key Path |
|---|---|---|
| `:app` | Android entrypoint, VPN service, Compose UI, domain decisions, private learning | `app/src/main/java/com/unblocker/app/` |
| `:common` | Shared packet/rule models, coroutine dispatcher abstractions | `common/src/main/java/com/unblocker/common/` |
| `:vpn-engine` | Reusable TUN builder, DNS parser/forwarder, VPN state APIs | `vpn-engine/src/main/java/com/unblocker/vpn/` |
| `:data-store` | Room v2 DB, DAOs, entities, migrations, rule caches, repositories | `data-store/src/main/java/com/unblocker/data/` |
| `:ml-engine` | Local heuristic domain scoring, WorkManager batch analysis | `ml-engine/src/main/java/com/unblocker/ml/` |

**Dependency graph:**
```
            :app
        /    |    \
 :vpn-engine :ml-engine :data-store
        \    |        /
           :common
```

> **Important:** The existence of a module does not mean it is on the active runtime data path. Trace Hilt bindings and actual callers before asserting what runs in production. In particular: `:ml-engine` uses `HeuristicDomainClassifier` (not TFLite despite some comments); `:vpn-engine` has DNS/VPN facilities that may not be what the manifest-bound service delegates to.

### Key Files for DNS Filtering Path

```
UnblockerVpnService.kt              ← TUN packet reader (IPv4/IPv6, UDP/TCP)
  app/.../services/UnblockerVpnService.kt

DnsPacketUtil + DnsResponseValidator ← wire-level decoding and validation
  app/.../logic/dns/

ContentFilterEngine.kt              ← routes DNS to blocking logic
  app/.../logic/ContentFilterEngine.kt

DecideBlockingUseCase.kt            ← central policy, decision precedence (see §Behavior Contracts)
  app/.../domain/usecase/DecideBlockingUseCase.kt

DnsUpstreamClient.kt                ← protected socket forwarding to Android-network resolver
  app/.../services/DnsUpstreamClient.kt

QueryLogSink.kt                     ← async bounded query logging → Room domain_stats
  app/.../data/logs/QueryLogSink.kt
```

**Decision precedence** (short-circuit, top wins):

| Order | Gate | Result |
|---|---|---|
| Exception | unexpected error | `ALLOW(ENGINE_ERROR)` — fail open |
| 1 | Invalid/malformed domain | `ALLOW(INVALID)` |
| 2 | Pause active | `ALLOW(PAUSED)` |
| 3 | User allow rule | `ALLOW(USER_ALLOW)` |
| 4 | User block rule | `BLOCK(USER_BLOCK)` |
| 5 | `APPROVED_ONLY` mode (no match) | `ALLOW(NO_MATCH)` |
| 6 | Protected infrastructure / NeverBlock | `ALLOW(PROTECTED)` |
| 6b | `MobileUnbreakList` essential domain | `ALLOW(ALLOWED)` |
| 7 | BASIC reward/shop exception | `ALLOW(ALLOWED)` |
| 7b | Admitted static/extension allow | `ALLOW(ALLOWED)` |
| 7c | Admitted static/extension block | `BLOCK(STATIC_RULE)` |
| 8 | Adaptive evidence crosses threshold | `BLOCK(LEARNED)` |
| 9 | Adult-content detector match | `BLOCK(adult reason)` |
| 10 | No match | `ALLOW(NO_MATCH)` |

### Key Files for UI

```
app/.../ui/MainScreen.kt                     ← root Compose navigation
app/.../ui/screens/                          ← Protection, Logs, Learn, Extensions
app/.../ui/settings/                         ← Settings UI
app/.../ui/logs/LogViewModel.kt              ← log search/filter state
app/.../ui/learn/LearnViewModel.kt           ← per-app UID recording session
app/.../ui/extensions/                       ← extension list UI
app/.../services/QuickStartManager.kt        ← VPN consent/lifecycle UI bridge
```

### Key Files for Learning / Privacy

```
app/.../logic/analysis/DeviceLearning.kt         ← HMAC-backed private rules
app/.../logic/analysis/PrivateEvidenceStore.kt   ← adaptive heuristic evidence
app/.../logic/analysis/NeverBlockPolicy.kt        ← always-allow infrastructure
app/.../data/logs/QueryLogSink.kt                ← query aggregates (plaintext domains!)
data-store/.../db/                               ← Room v2 schema + migrations
app/.../data/preferences/MyDomainsStore.kt       ← "My domains" plaintext list (up to 500)
```

---

## 6. How to Add a Feature

### Step-by-Step Process

- [ ] **1. Read** `AGENTS.md`, `PRODUCT.md`, `docs/BEHAVIOR_CONTRACTS.md`, and the relevant `docs/features/<topic>.md` (if it exists).
- [ ] **2. Load** the appropriate skill (see §10): `feature-implementation` for new features, `compose-ui-change` for UI, `dns-protocol-change` for DNS, `privacy-security-review` for data/security changes.
- [ ] **3. Plan** — write `docs/superpowers/plans/YYYY-MM-DD-<feature>.md` with: scope, affected modules, acceptance criteria, threat model, test plan, doc plan.
- [ ] **4. Write a failing test** — capture existing behavior first; write the regression test before the implementation.
- [ ] **5. Implement** — one bounded change. Keep changes minimal and backward-compatible.
- [ ] **6. Run test matrix** — see §7 for required test types per change class.
- [ ] **7. Update docs (in the same PR):**
  - [ ] Create or update `docs/features/<topic>.md`
  - [ ] Add a row in `docs/FEATURE_MATRIX.md`
  - [ ] Update `docs/BEHAVIOR_CONTRACTS.md` if decision logic changed
  - [ ] Update `PRODUCT.md`, `SECURITY.md`, `PRIVACY.md` if product/privacy claims changed
  - [ ] Update `CHANGELOG.md`
  - [ ] Update root `README.md` only if user-visible behavior changed
- [ ] **8. Run docs tooling** — four commands (see §11).
- [ ] **9. PR summary** — evidence-backed, no fabricated metrics.

### Required Test Types

| Feature area | Required tests |
|---|---|
| Any | JVM unit tests for new logic paths |
| DNS packet handling | IPv4/IPv6, UDP/TCP, malformed input, checksum |
| Blocking decision | Precedence cases from `BEHAVIOR_CONTRACTS.md` |
| Private rules/learning | No-plaintext-identifier, user-feedback compatible |
| Room / data-store | Migration tests (old schema → new), opt-in/clear/retention |
| UI | ViewModel state tests; connected UI instrumentation for consent/perms flows |
| VPN lifecycle | Boot, foreground service, network-change transitions |
| Filter list download | SSRF, size cap, source license, HTTPS redirect validation |

### Required Doc Updates

Always in the **same PR** as the code change. No doc update = PR is not done.

---

## 7. How to Write Tests

### Test Types

| Type | Location | Runner | Use for |
|---|---|---|---|
| **JVM unit** | `app/src/test/java/com/unblocker/app/` | `./gradlew testDebugUnitTest` | Logic, parsing, decisions, ViewModels |
| **Property / fuzz** | Same as unit tests (JVM) | Same | Malformed DNS input, rule parsing edge cases |
| **Room migration** | `data-store/src/androidTest/` | `./gradlew connectedDebugAndroidTest` | Schema v1→v2 correctness, upgrade |
| **Android instrumentation** | `app/src/androidTest/` | `./gradlew connectedDebugAndroidTest` | VPN consent, Keystore, boot, permissions |
| **Python compiler** | `tools/filter-compiler/` | `python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v` | Filter list parsing and compilation |
| **Script/docs tooling** | `scripts/tests/` | `bash scripts/tests/run-all.sh` | docs_sync, docs_contract, frontmatter |

### Change-to-Test Matrix

| Changed files | Must inspect / run | Extra gates |
|---|---|---|
| `logic/dns/`, `services/*Dns*`, `UnblockerVpnService` | dns tests, `connectedDebugAndroidTest` | IPv4/IPv6, checksum, bad replies, TCP, no network loops |
| `domain/usecase`, `logic/AdDetector`, `logic/rules` | `DecideBlockingUseCaseTest`, `RuleSetDifferentialTest`, `RuleLineParserTest` | Precedence + protected-domain false positives |
| `logic/analysis`, `DeviceLearning`, private rules | `EvidenceLearningTest`, `PrivateEvidenceStoreTest`, `PrivateDomainSetTest`, migration instrumentation | No plaintext identifiers, user-feedback compatible |
| `data-store/db`, `data/logs` | Room migration tests, `QueryLogSinkTest`, privacy regression | Opt-in/clear/retention, no loss/reappearance |
| `data/repository/BoundedFilterDownloader`, extension manifest | Downloader/repository/rollback tests, Python filter-compiler tests | HTTPS + SSRF + caps + source license |
| `ui/screens`, `ui/settings`, `ui/logs`, `ui/learn`, `ui/extensions` | ViewModel/mapper tests, connected UI instrumentation | States, accessibility, permissions, screen sizes |
| `receivers`, `QuickStartManager`, `AndroidManifest` | Lifecycle/manifest test + cold boot when possible | Exported flags, FGS, OEM limits |
| `build.gradle.kts`, `gradle/`, `.github/workflows` | Full JVM/lint/APK/tooling and release policy tests | Source/version/signing/CI policy |
| `docs/`, `.agents/`, `scripts/docs_*` | `docs_contract`, `docs_sync --check`, docs-tooling unittest | Valid skill/rule frontmatter, links, drift |

### Key Existing Test Anchors

```
app/src/test/java/com/unblocker/app/domain/DecideBlockingUseCaseTest.kt
app/src/test/java/com/unblocker/app/.../RuleSetDifferentialTest.kt
app/src/test/java/com/unblocker/app/.../RuleLineParserTest.kt
app/src/test/java/com/unblocker/app/.../EvidenceLearningTest.kt
app/src/test/java/com/unblocker/app/.../PrivateEvidenceStoreTest.kt
app/src/test/java/com/unblocker/app/.../PrivateDomainSetTest.kt
app/src/test/java/com/unblocker/app/.../QueryLogSinkTest.kt
```

> **Rule:** Test evidence is execution-specific. The existence of a test file is not a pass. Record the exact command, build SHA, date, device/emulator model and API level.

### Minimum Regression Cases (per BEHAVIOR_CONTRACTS.md)

Allow beats block; exact vs. suffix/whole-site; paused; error/invalid; protected infrastructure; user-block vs. protected conflict; `APPROVED_ONLY` unmatched allow; `BASIC` compatibility exceptions; adult toggle independent of ad toggle; extension allow priority; learned evidence expiry; memory reset; restart migration.

---

## 8. Definition of Done — PR Checklist

Taken verbatim from `docs/QUALITY_GATES.md`. Use this for every non-trivial PR:

- [ ] Scope, product intent and existing source behavior verified.
- [ ] No unapproved semantic change in `DecideBlockingUseCase` precedence.
- [ ] No DNS packet parsing/forwarding regression or new network loop.
- [ ] No plaintext private learning, unwanted logging/export, analytics or backup leak.
- [ ] No unsafe rule source, unchecked redirect/private-network target or license omission.
- [ ] Android permission, VPN lifecycle, boot/FGS and device network transitions respected.
- [ ] Unit/instrumentation/fuzz/migration cases added or explicitly marked not applicable.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` run or honestly marked `NOT RUN`.
- [ ] Python compiler, script hygiene and release checks run as relevant.
- [ ] Root/user docs, feature and module docs, changelog and behavior contracts reviewed.
- [ ] `python3 scripts/docs_sync.py --write` and `--check` run in repo checkout.
- [ ] `python3 scripts/docs_contract.py` and docs-tooling tests pass.
- [ ] Secret scan, dependency/license review, CI branch protections and signing unaffected.
- [ ] PR body records actual evidence, risk level, rollback and release decision.

> **Do not conflate check existence, CI success, and actual on-device production readiness.**

---

## 9. What Not to Touch / Pitfalls

### Files / Patterns That Require Explicit Review Before Changing

| File / Pattern | Risk |
|---|---|
| `DecideBlockingUseCase.kt` — decision order | Any re-ordering of steps 1–10 is a product/security decision |
| `APPROVED_ONLY` behavior | Currently allows unmatched hosts; changing to deny-all is a product decision |
| `NeverBlockPolicy.kt`, `MobileUnbreakList` | Changing protected-domain exceptions breaks connectivity |
| Room schema (`UnblockerMigrations.kt`, `@Database`) | Non-destructive migrations only; test before shipping |
| `QueryLogSink.kt` — retention/opt-in/clear | Privacy contract; any change requires `PRIVACY.md` update |
| `BoundedFilterDownloader.kt` — HTTPS redirect / size cap | SSRF / resource exhaustion risk |
| `app/src/main/assets/extensions/extensions_manifest.json` | Extension manifest; source license must be verified |
| `.github/workflows/release.yml` | Release signing; never weaken or add test-cert fallback |
| `gradle/verification-metadata.xml` | Dependency verification; never relax without security review |
| `.agents/rules/*.md`, `.agents/skills/*/SKILL.md` | Valid frontmatter required; `docs_contract` will fail otherwise |

### Common Agent Mistakes

| Mistake | Correct behavior |
|---|---|
| Reading `docs/generated/` as product truth | Generated files are machine-produced inventory — verify against source |
| Claiming tests passed without running them | Mark as `NOT RUN`; never fabricate evidence |
| Assuming `:vpn-engine` or `:ml-engine` code is on the active path | Trace callers and Hilt bindings first |
| Claiming all stored data is HMAC-hashed | Plaintext domains exist in `QueryLogSink` aggregates and `MyDomainsStore` |
| Claiming `APPROVED_ONLY` is a default-deny firewall | It currently `ALLOW(NO_MATCH)` for unmatched hosts |
| Changing `DecideBlockingUseCase` ordering as a "refactor" | That ordering is the product behavioral contract |
| Editing `docs/generated/*.md` by hand | These are auto-generated; run `docs_sync.py --write` instead |
| Silently picking the more flattering interpretation of a disagreement | Record in `docs/REPO_RECONCILIATION.md` and investigate |
| Adding public DNS fallback to the VPN resolver path | Explicit product/security decision required |
| Auto-merging or publishing without project approval | Never; always require a human-reviewed PR |
| Equating JVM test pass with on-device VPN behavior | They test different layers; label gaps honestly |

---

## 10. Available Agent Skills

Skills are located under `.agents/skills/`. Load a skill's `SKILL.md` before starting the relevant work.

> **Mandatory for every task:** Always load `.agents/skills/kotlin-senior-code-style/SKILL.md` for any Kotlin, XML, or Gradle work.

| Skill | Path | Description | When to Use |
|---|---|---|---|
| **kotlin-senior-code-style** | `.agents/skills/kotlin-senior-code-style/SKILL.md` | Four-space indent, 120-char lines, KDoc, lifecycle-safe Android patterns, immutable exposure | **Every** prompt involving Kotlin, XML or Gradle |
| **feature-implementation** | `.agents/skills/feature-implementation/SKILL.md` | End-to-end guide for adding a user-visible feature across Kotlin modules and settings | Adding or changing a user-visible feature |
| **dns-protocol-change** | `.agents/skills/dns-protocol-change/SKILL.md` | TUN packet handling, DNS parsing, upstream resolvers, caching, TCP fallback, VPN routing | Modifying DNS wire code or VPN routing |
| **privacy-security-review** | `.agents/skills/privacy-security-review/SKILL.md` | Query logs, HMAC rules, Keystore, Room, backups, extension URLs, Android permissions, trust boundaries | Any change touching stored data, logging, network security, or permissions |
| **compose-ui-change** | `.agents/skills/compose-ui-change/SKILL.md` | Jetpack Compose screens, navigation, themes, settings interactions, accessibility | Designing or modifying any UI screen |
| **android-testing** | `.agents/skills/android-testing/SKILL.md` | Emulator/device verification for VPN consent, Keystore, Room, boot, permissions | When a change needs emulator or device-level validation |
| **bug-investigation** | `.agents/skills/bug-investigation/SKILL.md` | Crashes, DNS false positives, VPN disconnects, slowdowns, stale logs, non-reproducible problems | Diagnosing a defect |
| **code-review** | `.agents/skills/code-review/SKILL.md` | PR review for correctness, security, Android behavior, documentation consistency | Reviewing any PR or generated code |
| **filter-list-update** | `.agents/skills/filter-list-update/SKILL.md` | Bundled domain datasets, extension source URLs, filter parser rules, blocklist admission | Adding/replacing blocklists or extension sources |
| **docs-maintainer** | `.agents/skills/docs-maintainer/SKILL.md` | Keeping docs in sync when implementation, module structure, or features change | Any change that makes existing docs stale |
| **release-verifier** | `.agents/skills/release-verifier/SKILL.md` | GitHub release, APK sign-off, version/tag change, published artifact validation | Preparing or validating a release |

---

## 11. Documentation Rules

### Two Distinct Update Lanes

**Lane A — Machine-generated inventory** (never hand-edit these):

```
docs/generated/*.md
```

- Produced by `scripts/docs_sync.py` from Gradle files, Kotlin source declarations, and extension JSON.
- Has a machine header and stable ordering.
- Run `--write` to regenerate; run `--check` to verify no drift.

**Lane B — Human-owned semantic docs** (update in the same PR as your code):

```
PRODUCT.md
SECURITY.md
PRIVACY.md   (root original — only when actually maintaining it)
docs/features/<topic>.md
docs/modules/<MODULE>.md
docs/BEHAVIOR_CONTRACTS.md
docs/FEATURE_MATRIX.md
CHANGELOG.md
README.md    (only if user-visible behavior changed)
```

> Scripts cannot safely infer promises, security claims, or feature completeness from symbol names. Human/agent review is required.

### Required Commands After Any Change

Run these **in order** from the repository root before committing:

```bash
# 1. Regenerate generated inventory from current source
python3 scripts/docs_sync.py --write

# 2. Validate behavioral contracts (feature/module/privacy docs)
python3 scripts/docs_contract.py

# 3. Verify no drift between source and generated inventory
python3 scripts/docs_sync.py --check

# 4. Run documentation tooling unit tests
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

Commit the resulting `docs/generated/` changes in the same PR.

### Full Build + Test Commands

```bash
# JVM tests, lint, and debug APK (Linux/macOS)
./gradlew testDebugUnitTest lintDebug assembleDebug

# Android device/emulator instrumentation tests
./gradlew connectedDebugAndroidTest

# Python filter compiler tests
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v

# All script checks
bash scripts/tests/run-all.sh
```

> See `docs/BUILD_AND_TEST.md` for detailed build facts. See `docs/RELEASE_PROCESS.md` for production signing/release instructions.

### When Documentation Checks Fail

| Failure type | Correct action |
|---|---|
| Drift | Run `--write` in updated source checkout, review diff, commit |
| Broken source link | Correct the path, or mark the page as intentionally historical |
| Frontmatter invalid | Repair `.agents/rules/*.md` trigger or `.agents/skills/*/SKILL.md` required fields |
| Missing business meaning | Inspect code and acceptance cases; a script cannot fix this |

### Automation

- **PR to main:** `.github/workflows/documentation-check.yml` checks contracts and drift against changed source.
- **Push to main / scheduled / manual:** `.github/workflows/documentation-sync.yml` writes generated Markdown and proposes a PR via `gh pr create`. **Never directly pushes to main.**
- If the bot PR is blocked by repository permissions, manually run `--write` and open a human-reviewed PR.

---

*This guide is maintained under Lane B (human-owned). Update it when the workflow, constraints, module structure, or skill directory changes. Source snapshot: `main` at `8934519` (2026-11-10).*
