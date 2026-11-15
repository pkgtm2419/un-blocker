---
name: dns-protocol-change
description: Use when modifying TUN packet handling, DNS parsing, upstream resolvers, caching, TCP fallback or VPN routing.
---

# Dns Protocol Change

## Workflow
1. Inspect manifest-bound `app/services/UnblockerVpnService.kt` and caller chain first; do not assume `:vpn-engine` classes are the active implementation.
2. Map input/output wire structures, IPv4/IPv6, DNS header/EDNS/TCP/UDP, timeouts, buffer ownership and resolver epoch. Read `docs/DNS_PIPELINE.md` and `docs/BEHAVIOR_CONTRACTS.md`.
3. Write malformed and valid packet regression tests: short headers, compression loops, length overflow, IPv6 checksums, TXID/QTYPE/QCLASS mismatch, truncated UDP with validated TCP retry, no resolver, network transition and socket protection.
4. Keep async worker bounded, never intercept general TLS or add public fallback to active path without approved change. Treat DNS responses as untrusted.
5. Run app DNS/services JVM suite, affected `vpn-engine` tests, `connectedDebugAndroidTest` on disposable emulator if available; inspect packet performance.
6. Request `/privacy-security-review`, update `docs/DNS_PIPELINE.md`, `NETWORK_COVERAGE.md`, runtime architecture, test matrix and generated inventory.

## Example
Refactoring response validation must reject a mismatched QCLASS even when TXID and domain match.

## Mistake
Confusing synthetic `TunnelDnsConfig` endpoints with allowed Android-network upstream resolver addresses, or copying historical tunnel addresses without inspecting current code.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
