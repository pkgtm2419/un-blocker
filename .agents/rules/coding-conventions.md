---
trigger: model_decision
description: General Kotlin, Gradle and Python changes affecting project coding style or maintainability
---

# Coding Conventions

Apply `.agents/skills/kotlin-senior-code-style/SKILL.md` on every prompt. Follow `docs/CODE_STANDARDS.md`, real module ownership and Kotlin style already in source. Use typed outcomes, bounded functions, safe concurrency and clear error messages. Tests first for behavior changes. Respect module layering, published interfaces, `gradle/libs.versions.toml` and Keystore/Room rules. No new telemetry, unbounded threads, accidental I/O on DNS hot path or suppressed lint to bypass failures. Validate actual source behavior and update docs in same PR.
