---
name: code-review
description: Use when reviewing a pull request or generated code for correctness, security, Android behavior and documentation consistency.
---

# Code Review

## Review priorities
1. Compare actual diff to requirement and acceptance criteria; trace impacted runtime path and typed interface changes, not only summaries.
2. Focus on critical defects: user override order, malformed DNS handling, IPv6/TCP, resolver loop, private logging and migration, HTTPS list fetch/SSRF and secrets/signing.
3. Read tests and rerun available commands; identify untested failure modes. Distinguish observed defects, plausible risks and unverified assumptions, with code locations.
4. Check module responsibility, coroutine lifecycle, bounds, Room schema exports, Compose state and Android permissions.
5. Ensure docs and generated inventories updated in same PR; `docs_contract` and `docs_sync --check` must pass after bootstrap.
6. Return actionable comments ordered by severity, including specific reproduction/test and recommendation. Do not approve based on CI badge alone.

## Example
If PR asserts "no raw DNS names persisted," point to `domain_stats.domain` and request precise privacy update/controlled test.

## Mistake
Reporting stale documentation as a fixed security bug without verifying runtime code.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
