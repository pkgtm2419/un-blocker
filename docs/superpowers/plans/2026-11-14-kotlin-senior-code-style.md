# Kotlin Senior Code Style Implementation Plan

> **For agentic workers:** Use Superpowers execution and review guidance. Work file by file; maintain the progress record below.

**Goal:** Analyze all Android modules, install automatically required senior Kotlin style guidance, and apply it throughout source without behavior/API changes.

**Architecture:** A project skill and always-on instructions define semantic review. EditorConfig and pinned ktlint enforce mechanical rules; per-file KDoc/naming review and JVM/Android checks protect contracts.

**Tech Stack:** Kotlin 2.0.21, Compose, Hilt, Room, Gradle 8.11.1, JDK 17, SDK 35, Python tooling, pinned ktlint.

**Spec:** [Design](../specs/2026-11-14-kotlin-senior-code-style-design.md), including the user's detailed requirements.

## Global Constraints

- Preserve existing behavior, public signatures/named arguments, database/JSON/file keys, Android manifests and signing policy.
- Four-space indentation, 120-character maximum for maintainable code; explicit exceptions only for immutable literals/API names.
- Do not remove Android/reflection entrypoints or feature behavior to satisfy style.
- Preserve prior uncommitted documentation work and saved inputs.

## Review Focus

Kotlin string literals must not change; inline/annotation/Compose syntax must remain valid; public named arguments must retain names; Kotlin constructor properties need documentation; automated formatter import removal needs compilation verification.

### Task 1: Inventory and baseline

- [x] Inventory five modules, source/resource/build tree and existing agent guidance.
- [ ] Install portable approved toolchain and record baseline tests.
- [ ] Save source baseline and architecture/style observations.

### Task 2: Skill and repeatable enforcement

- [ ] Run a no-skill compatibility scenario; write the requested skill addressing observed gaps.
- [ ] Add root every-prompt instruction, always-on rule, EditorConfig and pinned check entrypoint.
- [ ] Validate skill metadata and behavioral scenario.

### Task 3: Apply per-file standards

- [ ] Review/document production public declarations in all modules.
- [ ] Review test code, build scripts, XML and safe private naming/constants.
- [ ] Apply Kotlin-aware formatting/import checks and review remaining findings.

### Task 4: Verify and deliver

- [ ] Run style, API/syntax, JVM, lint, assembly and documentation checks.
- [ ] Obtain independent review and fix actionable defects.
- [ ] Generate analysis, skill content, per-file summaries and full updated source artifact.

## Exit gates

Report exact commands and failures. No Android runtime certification, commit or push implied. Record compatibility exceptions rather than breaking public APIs for appearance.
