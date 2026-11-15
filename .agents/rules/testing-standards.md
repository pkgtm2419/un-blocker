---
trigger: model_decision
description: When adding or updating Kotlin tests, Python tooling tests, Android emulator checks or CI quality gates
---

# Testing Standards

Follow `docs/TEST_MATRIX.md` and `docs/TESTING_STRATEGY.md`. For bugs reproduce red first; cover invalid input, concurrency, pause, private data, migration, IPv6 and network changes. Gradle, Python and instrumentation tests must be reported by exact command/result; existence of tests is not evidence of execution. Do not weaken false-positive corpus, test fixtures or checks.
