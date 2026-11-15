# Un-Blocker Coding Standards

> **Canonical sources:** This document consolidates
> [`docs/CODE_STANDARDS.md`](docs/CODE_STANDARDS.md),
> [`.agents/skills/kotlin-senior-code-style/SKILL.md`](.agents/skills/kotlin-senior-code-style/SKILL.md),
> [`.agents/rules/coding-conventions.md`](.agents/rules/coding-conventions.md), and
> [`.agents/rules/testing-standards.md`](.agents/rules/testing-standards.md).
> When those files disagree with this document, the referenced source files take precedence.
> See [`AGENTS.md`](AGENTS.md) for the full workspace contract.

---

## Table of Contents

1. [Formatting](#1-formatting)
2. [Naming Conventions](#2-naming-conventions)
3. [Documentation (KDoc)](#3-documentation-kdoc)
4. [Architecture Rules](#4-architecture-rules)
5. [Android & Coroutines](#5-android--coroutines)
6. [DNS & Security Specifics](#6-dns--security-specifics)
7. [Persistence Rules](#7-persistence-rules)
8. [Testing Standards](#8-testing-standards)
9. [Pull Request Standards](#9-pull-request-standards)
10. [Gradle & Build Configuration](#10-gradle--build-configuration)
11. [Python Tooling (scripts/)](#11-python-tooling-scripts)
12. [Compatibility Decisions](#12-compatibility-decisions)
13. [Common Mistakes to Avoid](#13-common-mistakes-to-avoid)
14. [Quick Reference — Repeatable Checks](#14-quick-reference--repeatable-checks)

---

## 1. Formatting

> [!IMPORTANT]
> The pinned formatter is the authority on formatting. Editor settings come from `.editorconfig`.
> CI enforces formatting independently via `kotlin-style.yml`.

| Setting | Value |
|---|---|
| Indentation | **4 spaces** — no tabs |
| Line length | **120 characters** maximum |
| Spacing | Official Kotlin spacing rules |
| Imports | Ordered per Kotlin conventions; remove all unused imports |
| Formatter | ktlint **1.8.0** (checksum-pinned) |
| JDK requirement | JDK 17+ |

### Running the formatter

```bash
# One-time setup: fetch the pinned ktlint binary
python scripts/kotlin_style.py --download-tool

# Check formatting (CI mode — does not change files)
python scripts/kotlin_style.py

# Fix formatting automatically
python scripts/kotlin_style.py --format
```

> [!NOTE]
> Formatting compliance does not prove API compatibility or architectural correctness. After
> auto-formatting, review every changed file for correctness against public signatures and
> executable paths.

A long immutable value (URL, regex, SQL, fixture) must **not** be split in a way that changes its
value. Wrap surrounding syntax or comments first; document an unavoidable line-length exception
explicitly rather than disabling style checks broadly.

---

## 2. Naming Conventions

| Construct | Convention | Notes |
|---|---|---|
| Classes, interfaces, objects | `PascalCase` | |
| Functions, properties, local variables | `camelCase` | |
| Compile-time constants | `UPPER_SNAKE_CASE` | `const val` **only** for supported compile-time types |
| Loop indices | Single letters (`i`, `j`) | Only acceptable single-letter names |
| `@Composable` UI functions returning `Unit` | `PascalCase` | Follows Compose API guidelines — **not** camelCase |
| Android lifecycle overrides / externally required names | Retain required spelling | Do not rename to satisfy style |

Use **descriptive names** that reveal intent. Avoid abbreviations unless they are domain-standard
(e.g., `Dns`, `Vpn`, `Hmac`). Explain existing abbreviations in KDoc rather than silently renaming
a public API.

Do **not** rename:
- Room column names or entity fields
- Serialized/preference keys
- DNS format identifiers
- Android resource names or manifest component names

---

## 3. Documentation (KDoc)

> [!IMPORTANT]
> KDoc is mandatory for every **source-declared** public class, function, and property.

### What to document

- **Purpose** — what the element does (not just what it is named)
- **Parameter meaning and units** — especially for byte counts, timeouts, lengths, flags
- **Meaningful return values** — when the return is non-obvious
- **Actual exceptions** — only exceptions that are genuinely thrown; never guess `@throws`
- **Non-obvious invariants** — thread ownership, coroutine scope, buffer ownership, protocol constraints

### KDoc parameter tags

```kotlin
/**
 * Resolves [domain] against the active upstream resolver and returns a [DnsAnswer].
 *
 * @property timeoutMs Maximum time to wait for a response, in milliseconds.
 * @param domain The normalized, ASCII-compatible-encoded hostname to resolve.
 * @return A [DnsAnswer] containing A/AAAA records, or [DnsAnswer.empty] on NXDOMAIN.
 * @throws DnsTimeoutException if no response is received within [timeoutMs].
 */
```

- Use `@property` for public **constructor** properties.
- Use `@param` for all other function parameters.
- Use `@return` when the return value meaning is not obvious from the type.
- Use `@throws` only for exceptions the function **actually** throws.

### What NOT to document

- Obvious getters: a one-line "Gets X" comment adds no value.
- Override implementations that add no new semantics — document through the owning type.
- Thread safety claims without evidence. Do not write "thread-safe" unless it is verifiably true.
- Fictional return value documentation for `Unit` functions.

---

## 4. Architecture Rules

### Core principles

- **One responsibility per class and per function.** If you need "and" to describe what something
  does, consider splitting it.
- **Short, cohesive functions.** Prefer early returns and shallow nesting over deeply nested
  conditionals.
- **Separate concerns.** UI, domain logic, and data access must not bleed into each other:
  - Composables must not access the database or network directly.
  - Domain use-cases must not import Android UI types.
  - Data repositories must not contain business rules.

### Dependency injection

- Use existing **Hilt** injection boundaries. Do not construct dependencies manually where Hilt
  already manages them.
- Use `internal` visibility for implementation details; expose only minimal public contracts.

### Type system

| Preference | Guidance |
|---|---|
| `val` over `var` | Immutability by default |
| Immutable collections | Expose `List`/`Set`/`Map`, not `MutableList` etc. |
| Sealed outcomes | Use typed `sealed class`/`sealed interface` for results and error states instead of magic values |
| `data class` | For immutable records that express domain concepts |
| Extension functions | Focused and scoped; avoid utility dumping grounds |
| Explicit null handling | No `!!` without a documented invariant proving non-null |

---

## 5. Android & Coroutines

> [!CAUTION]
> Never block the **main thread** or the **DNS hot path**. Violations here directly degrade VPN
> performance and UI responsiveness.

### Lifecycle and state

- Respect lifecycle ownership. Collect `Flow`/`StateFlow` only within a lifecycle-aware scope
  (`repeatOnLifecycle`, `collectAsStateWithLifecycle`).
- Hoist state to `ViewModel`; expose it as **read-only** `StateFlow`.
- One source of truth — never duplicate state across UI and domain layers.

### Coroutines

- Use **structured concurrency**: launch coroutines in a `CoroutineScope` tied to a lifecycle or
  Hilt-injected scope.
- **Propagate cancellation** — do not catch `CancellationException` and suppress it.
- Always clean up resources in `finally` blocks or use `use {}`.
- **No `GlobalScope`** — it leaks coroutines across the app lifecycle.
- **No unbounded threads** — use dispatcher-bound coroutines or bounded thread pools.

### I/O discipline

- All database, file, and network I/O must run on `Dispatchers.IO` or an equivalent background
  dispatcher.
- Use timeouts and bounded queues. Document the timeout policy in KDoc.

---

## 6. DNS & Security Specifics

> [!CAUTION]
> The VPN intercepts real user traffic. Errors here can expose private data, stall connections,
> or silently mis-filter domains. Apply these rules without exception.

### Input trust

**Never trust:**

- `rawDomain` values from DNS wire packets
- Packet bytes from the TUN interface
- Answers from upstream DNS resolvers
- Contents of remotely downloaded extensions or filter lists

**Always:**

- Normalize input **once** at the entry point; pass the normalized form through the pipeline
- Validate lengths before processing; reject malformed input at the boundary
- Use **checked arithmetic** where byte offsets or lengths could overflow
- Document the normalization contract in KDoc

### Buffer and concurrency safety

- Do not mutate shared byte buffers across background coroutines without documented synchronization.
- Keep buffer ownership explicit: document who owns a buffer at each call boundary.
- Prefer local/copied buffers on the DNS hot path.

### DNS hot path

- Minimize allocation and locking under load.
- Do not introduce locking or allocation that scales with request rate unless unavoidable, and
  document any such exception.
- Preserve all wire-format correctness and resolver invariants during refactors.
- Document protocol field sizes, limits, units, and thresholds by name — do not use magic numbers.

### Error handling

- Handle failures with existing typed outcomes (sealed classes).
- Document the error policy in KDoc — what happens when a resolver fails, times out, or returns
  malformed data.
- Never swallow failures and falsely report protection.

---

## 7. Persistence Rules

> [!WARNING]
> Schema migrations that destroy data violate the backward-compatibility guarantee. Non-destructive
> migration is an absolute requirement.

### Room (database)

- Use the existing Room entity and DAO patterns.
- **Every schema change requires:**
  1. A new Room `@Database` version number.
  2. A corresponding `Migration` object (non-destructive — `ALTER TABLE`, `CREATE TABLE`, etc.).
  3. The updated schema JSON file committed to version control.
- Do not rename columns, change column types destructively, or drop tables without a migration.
- Never set `fallbackToDestructiveMigration()` in production configurations.

### HMAC identifiers (learning and allow/block rules)

- Store learned domain identifiers as **HMAC hashes**, not plaintext.
- Use the existing Keystore-backed HMAC storage pattern. Do not introduce new raw domain storage
  for the learning or rules system.
- **Exception documented in `AGENTS.md`:** Local query-log aggregates and the My Domains protection
  list may contain plaintext domains; do not claim all stored data is hashed.

### Android backup

- Private domain data must not be included in Android Auto Backup. Verify `android:allowBackup`
  and `fullBackupContent` / `dataExtractionRules` configuration when adding new persisted data.

---

## 8. Testing Standards

> [!IMPORTANT]
> Never claim tests passed without actually running them. Report exact commands and outputs.

See [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) and [`docs/TESTING_STRATEGY.md`](docs/TESTING_STRATEGY.md)
for the full test inventory and strategy.

### Test placement

| Test type | Location |
|---|---|
| JVM unit tests | `app/src/test/` |
| Android instrumentation tests | `app/src/androidTest/` |
| Python tooling tests | `tools/filter-compiler/` and `scripts/tests/` |

### When to run instrumentation tests

Run Android instrumentation tests (connected device or emulator) for any change touching:

- VPN lifecycle or `UnblockerVpnService`
- Keystore-backed operations
- Room schema migrations
- Android permission or boot-receiver behavior

### Test quality rules

- **Write a failing (red) test before fixing a bug** — the regression test must exist first.
- Cover: invalid input, boundary values, concurrency, activity/fragment lifecycle pause/resume,
  private data handling, database migration, IPv6, and network state changes — wherever applicable.
- **Never disable, comment out, or weaken existing tests** to make CI green.
- Do not weaken false-positive corpus entries, test fixtures, or quality gate thresholds.
- Existence of a test file is not evidence of test execution. Report the actual command run and
  its result.

### Running tests

```bash
# JVM unit tests + lint + debug build
./gradlew testDebugUnitTest lintDebug assembleDebug

# Android instrumentation tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Python filter-compiler tests
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v

# All script checks
bash scripts/tests/run-all.sh

# Documentation sync and contract checks
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

---

## 9. Pull Request Standards

Every pull request must include:

| Requirement | Details |
|---|---|
| **Feature scope** | Clear statement of what changed and why |
| **Acceptance criteria** | How to verify the change is correct |
| **Root cause** (for bugs) | The verified root cause, not a guess |
| **Regression test** | Red → green test, or documented equivalent verifiable reasoning |
| **Build/test evidence** | Exact commands run and their outputs; mark explicitly if any check was NOT RUN |
| **Privacy/security assessment** | Whether the change touches data, network, permissions, or secrets |
| **Same-PR documentation** | Update `docs/FEATURE_MATRIX.md`, affected module docs, behavior contracts, changelog — in the same PR |
| **No secrets or logs** | No credentials, signing files, real browsing logs, or invented benchmarks |

> [!NOTE]
> See [`docs/QUALITY_GATES.md`](docs/QUALITY_GATES.md) for the full quality gate checklist.

After implementation, always run:

```bash
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
```

Commit any resulting `docs/generated/` inventory changes in the same PR.

---

## 10. Gradle & Build Configuration

- Keep all plugin and dependency versions in **`gradle/libs.versions.toml`**.
- Preserve declared module layering (`:common`, `:data-store`, `:vpn-engine`, `:ml-engine`, `app`).
  Do not create circular dependencies.
- Do not add new dependencies, raise SDK versions, change release signing, or suppress lint
  without justification **and** test evidence.
- Do not set `fallbackToDestructiveMigration()` in production.
- Never add credentials, proprietary binaries, or unlicensed list content to the repository.

---

## 11. Python Tooling (scripts/)

| Requirement | Rule |
|---|---|
| Python version | 3.11+ |
| Dependencies | Standard library only — no third-party packages |
| Output | Deterministic sorting and output; UTF-8 encoding throughout |
| Timestamps | No injected timestamps in generated output |
| Network | No network access |
| Privacy | No user private data in outputs or logs |
| File scans | Bounded — do not walk unbounded directory trees |
| `--check` mode | Must **never** mutate any file |
| `--write` mode | May only change files under **generated paths** |
| Exit codes | Explicit, documented exit codes for each failure condition |
| Errors | Actionable error messages (not bare stack traces) |

---

## 12. Compatibility Decisions

These rules govern situations where style and compatibility conflict.

### Public API preservation

- Preserve public declarations **and their parameter names**. Kotlin callers can use named arguments;
  renaming a public parameter is a breaking change.
- Explain existing abbreviations in KDoc rather than silently renaming them.
- Rename private or local identifiers only after checking all references, shadowing, labels, and
  string templates.

### Compose functions

- `@Composable` functions that return `Unit` **must** use `PascalCase`, following the Compose API
  guidelines. This is not a mistake; do not convert them to `camelCase`.

### Constants

- Use `const val` **only** for values that are genuinely compile-time constants of supported types
  (`Int`, `Long`, `Double`, `Float`, `Boolean`, `String`).
- When extracting a constant, do not change overflow behavior, unsigned behavior, byte order, or
  evaluation timing.

### Removing code

- Remove code **only after proving it is unused**.
- Hilt providers, Room entities/DAOs, Android lifecycle callbacks, reflection targets, and public
  APIs may be used without an ordinary source call site — do not remove them because no call site
  is visible in the editor.

---

## 13. Common Mistakes to Avoid

> [!WARNING]
> These are the most frequent style and correctness errors observed in this codebase.

| Mistake | Correct approach |
|---|---|
| Renaming a public parameter for aesthetics | Explain the name in KDoc; leave the parameter name unchanged |
| `camelCase` on a `@Composable` UI function | Use `PascalCase` — it's required by Compose conventions |
| Guessing `@throws` tags | Only document exceptions the function actually throws |
| Replacing all numeric literals mechanically | Name only the literals that have domain meaning; leave obvious sizes/indices as-is |
| Changing a lock or coroutine scope during formatting | Formatting is not a license to alter concurrency semantics |
| Removing a Hilt provider or Room DAO because it appears unused | These can be used via reflection or DI framework — prove it unused before removing |
| Claiming checks passed without running them | Run the commands; paste the output |
| Blocking the DNS hot path with I/O | All I/O off the hot path, always |
| Using `GlobalScope` | Use a lifecycle-scoped or Hilt-injected `CoroutineScope` |
| Storing raw domains in HMAC-protected fields | Use the existing HMAC pattern |
| Destructive Room migration | Write a non-destructive `Migration`; update schema JSON |
| Disabling a test to fix CI | Fix the test or the code; never disable |

---

## 14. Quick Reference — Repeatable Checks

Run these commands before opening a PR:

```bash
# 1. Kotlin formatting check
python scripts/kotlin_style.py

# 2. Full JVM test + lint + build
./gradlew testDebugUnitTest lintDebug assembleDebug

# 3. Documentation sync (generates inventory, checks contracts)
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v

# 4. Python filter-compiler tests
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v

# 5. Android instrumentation tests (if VPN/Keystore/Room/permissions changed)
./gradlew connectedDebugAndroidTest
```

**Official references:**

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [KDoc Reference](https://kotlinlang.org/docs/kotlin-doc.html)
- [Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md)
- [`docs/BUILD_AND_TEST.md`](docs/BUILD_AND_TEST.md) — build facts and environment setup
- [`docs/QUALITY_GATES.md`](docs/QUALITY_GATES.md) — PR quality gate checklist
- [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md) — test coverage matrix
- [`docs/TESTING_STRATEGY.md`](docs/TESTING_STRATEGY.md) — testing strategy detail
- [`AGENTS.md`](AGENTS.md) — full workspace contract for agents and contributors
