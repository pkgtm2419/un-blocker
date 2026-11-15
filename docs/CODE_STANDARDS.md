# Coding standards and formatting

## Kotlin / Android

- Apply `.agents/skills/kotlin-senior-code-style/SKILL.md`: four spaces, 120-character lines, official Kotlin conventions, ordered imports, accurate public KDoc and compatible descriptive names. Prefer idiomatic, explicit API contracts and short methods with a single responsibility.
- Use meaningful class/method names; typed `sealed` result and reason enums instead of magic values; `data class` for immutable records; internal visibility for implementation details. KDoc public entrypoints and protocols.
- Keep I/O and network off UI and TUN hot-path threads; use structured coroutines, cancellation checks, timeouts and bounded queues, and immutable `StateFlow` exposure. Do not introduce `GlobalScope` or unbounded threads.
- For Compose prefer hoisted state, lifecycle-safe collection, stable keys, one source of truth, accessible semantics and reusable `ui/theme` tokens. Avoid direct database/network access inside composables.
- Never trust `rawDomain`, packet bytes, upstream DNS answers or remote extension contents. Normalize once; check lengths; use checked arithmetic; avoid mutating shared buffers across background coroutines.
- Android permissions/services/exports and user-visible errors must be explicit. Do not swallow failures and falsely report protection.
- DNS path: minimize allocation/locking under load without sacrificing wire correctness or clarity. Keep packet and resolver invariants documented.
- Persistence: use the existing Room and HMAC storage patterns; migrations must be non-destructive and schema JSON updated in version control.
- Record `why` (not just `what`) for security, protocol or compatibility exceptions.

## Gradle and Python

- Keep build plugin and dependency versions in `gradle/libs.versions.toml` and preserve declared module layering. No extra dep, SDK bump, release signing change or lint suppression without justification + tests.
- Python 3 stdlib for documentation tools; deterministic sorting/output, UTF-8, no injected timestamps, no network or user private data, bounded file scans.
- Script outputs include explicit exit codes and actionable errors; `--check` never mutates files; `--write` changes generated paths only.

## Pull request acceptance

Feature scope, acceptance criteria and reproduction; a red→green regression test or equivalent verifiable reasoning; root cause fix; checked build/test commands; privacy/security impact; docs updated in same PR; no secrets or real traffic logs. [QUALITY_GATES.md](QUALITY_GATES.md).

## Repeatable Kotlin checks

Run `python scripts/kotlin_style.py --download-tool` once to fetch the checksum-pinned ktlint 1.8.0 CLI, then `python scripts/kotlin_style.py` to check or `python scripts/kotlin_style.py --format` to correct supported formatting. JDK 17+ is required; Android SDK is required only for Android build/test verification. The new `kotlin-style.yml` workflow checks formatting independently of the existing Android lint configuration. Skill/agent guidance covers semantic design and documentation; formatting alone does not prove API compatibility or architectural quality.
