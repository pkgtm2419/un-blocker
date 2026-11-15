---
trigger: model_decision
description: Changes to persistent data, logging, rule downloads, network security, permissions, secrets, security docs or CI
---

# Privacy Security

Read `SECURITY.md`, `docs/PRIVACY_DATA_FLOW.md`, `docs/SECURITY_REVIEW.md`. No plaintext learned-host persistence, remote telemetry, Logcat domains, unbounded raw histories, secret keys in source or unsafe network fetch. Local Room aggregates already contain readable domains: do not erase that distinction. Use `/privacy-security-review`, add adversarial tests, and record observed versus hypothesis.

The My domains protection list also stores readable domains in private preferences; HMAC claims apply to the separate DeviceLearning rule/evidence stores. Raw-history mode has no production enabling caller in the current checkout. Retention is periodic maintenance, not a strict per-insert bound.
