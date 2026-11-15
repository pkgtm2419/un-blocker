---
trigger: always_on
description: Mandatory Kotlin and Android code standards for every Un-Blocker prompt
---

# Kotlin senior code style

For every workspace prompt, load `.agents/skills/kotlin-senior-code-style/SKILL.md` and apply it to code involved in the task. Use four spaces, 120-character lines, descriptive compatible names, accurate public KDoc and lifecycle/thread-safe Android patterns. Preserve public APIs, named arguments, persistence formats and DNS behavior. Keep Compose UI function names and Android override names compatible. Use the pinned formatter/checks; report verification and any unavoidable compatibility exception. A non-code prompt does not require unrelated source changes.
