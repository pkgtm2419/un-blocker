---
name: privacy-security-review
description: Use when code changes touch query logs, HMAC rules, Keystore, Room, backups, extension URLs, Android permissions or network trust boundaries.
---

# Privacy Security Review

## Review steps
1. Identify trust boundary, changed stored fields, data leaving device, permissions and affected attack surfaces from `SECURITY.md` and `docs/SECURITY_REVIEW.md`.
2. Verify HMAC/private learning vs **plaintext local aggregate logs**; inspect QueryLogSink default, raw opt-in, retention, pause, clear generation barriers, backup/XML and CSV export.
3. Adversarial tests for DNS malformed/spoofed answers, HTTPS redirects to private IPs, DNS rebinding/TOCTOU, size caps, Keystore failures, migration and secret exposure.
4. Trace active resolver socket and alternate `:vpn-engine` defaults before stating there is/no public DNS fallback. Android Private DNS/DoH are not universally filtered.
5. Report Verified/Source-observed/Hypothesis/Unverified separately; do not publish exploit details or falsely assert an independent audit.
6. Require tests and corrections to `PRIVACY.md`/`SECURITY.md`, data-flow docs and reconciliation as warranted before approval.

## Example
A change to `DomainLogDao` retention must update source-backed privacy timeline, verify the clear barrier and test raw history opt-in.

## Failure mode
Assuming all DNS names are HMAC-hashed because learned rule IDs are.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
