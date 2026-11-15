# Kotlin/Android project and coding-standard analysis — 2026-11-14

## Scope and existing structure

Baseline application commit: `8934519b021d56f12e90d824c20b4f60be1bda4b`. Prior documentation integration is retained as uncommitted work. This request uses local branch `codex/kotlin-senior-code-style`.

| Module | Production Kotlin | JVM test files | Instrumentation files | Responsibility |
| --- | ---: | ---: | ---: | --- |
| app | 102 | 82 | 3 | Android entrypoints, manifest VPN, domain policy, local learning, UI and app repositories |
| common | 5 | 0 | 0 | Shared models and dispatchers |
| data-store | 19 | 2 | 0 | Room v2, DAOs, migrations, indexes and repositories |
| ml-engine | 8 | 0 | 0 | Deterministic heuristic features/classifier and batch-worker infrastructure |
| vpn-engine | 11 | 1 | 0 | Reusable DNS/VPN transport components |

Reviewed source set: **233 Kotlin files, seven Gradle Kotlin scripts, 13 XML files**. The 13 XML files comprise five module manifests and eight app resource files. PNG/icon assets, vendored filter datasets, database schema JSON, generated outputs and machine-local configuration are not rewritten as Kotlin source.

App packages: `data` (preferences, repository, logs, exports/models), `domain` (decisions/usecase), `logic` (DNS, filtering, rules and learning), `services` (VPN, upstream, health and recording), `receivers`, `di`, and Compose `ui` (screens, model, navigation/components, themes, motion). Module edges are documented in [architecture](../ARCHITECTURE.md) and derived in [generated inventories](../generated/README.md).

Build facts: Kotlin 2.0.21, AGP 8.10.1, Gradle 8.11.1, JVM target/JDK 17, min SDK 26 and compile/target SDK 35. Application version 2.6.1/code 35. Existing version catalog, dependency verification, Room schema exports and release scripts remain authoritative.

## Existing agent configuration

Before this task: root `AGENTS.md`, `.agents/rules/` with eight scoped rules, ten `.agents/skills/*/SKILL.md` entries and their UI metadata, `.editorconfig`, engineering docs and `.gsd` historical planning templates. No matching `kotlin-senior-code-style` skill, Cursor rules, CLAUDE/GEMINI instruction files, detekt configuration or ktlint enforcement was found in maintained project files.

Existing skills: docs-maintainer, feature-implementation, dns-protocol-change, privacy-security-review, filter-list-update, android-testing, compose-ui-change, bug-investigation, code-review, release-verifier. Existing rules cover coding conventions, DNS, docs lifecycle, dependencies, privacy/security, release safety, testing and Compose.

## Missing items addressed

- A dedicated senior Kotlin skill: now `.agents/skills/kotlin-senior-code-style/SKILL.md`.
- Every-prompt loading: explicit root agent instruction plus an `always_on` Antigravity rule. `allow_implicit_invocation` alone is not a universal activation guarantee; cooperating agents must follow the workspace instruction.
- Mechanical formatting: four spaces, 120-column configuration, official ktlint style, ordered/unused import checks and a checksum-pinned runner/CI gate.
- Public documentation: a Kotlin PSI audit identified 1,629 public source declarations; 937 production and 458 test declarations lacked documentation under the audit criteria. Constructor properties are covered by `@property`; private/internal declarations and compiler-generated members are treated separately.
- Environment: configured SDK/JDK paths no longer existed. Portable JDK 17 and SDK 35 were installed under ignored `.tooling/` with user approval; local SDK configuration updated without changing checked-in build policy.

## Compatibility and quality observations

Public API/named-argument spelling is preserved even where legacy abbreviations conflict with ideal naming. Compose UI functions retain PascalCase; Android overrides retain platform-required spelling. Resource names, preference/JSON/Room keys, wire formats, filter decisions and signing behavior are compatibility constraints.

Existing production comments overstated logging privacy, query-log retention, inference, scheduling, performance and protection priority. Comments are reconciled to actual implementation; user-visible strings and functional issues are not silently changed by style work. In particular, readable My domains preferences and local Room aggregates are distinct from HMAC rule/learning identifiers.

Lint configuration already suppresses several checks. DNS-only coverage, release test-signing fallback, conditional extension-rule broadening and wildcard allowance issues remain recorded in [reconciliation](../REPO_RECONCILIATION.md). Formatting does not remediate those behavior issues.

## Sources and verification

Kotlin/KDoc and ktlint configuration were checked with the user-selected Context7 plugin against [official Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html), [KDoc](https://kotlinlang.org/docs/kotlin-doc.html) and [ktlint](https://pinterest.github.io/ktlint/latest/). Android tool installation follows [Android command-line tools](https://developer.android.com/tools).

Detailed final commands/results, per-file summaries and full updated code are provided in the task's final review artifact. Android runtime results are not inferred from formatting, PSI parsing or JVM tests.
