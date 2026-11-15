# Security policy and engineering boundaries

This file documents development and vulnerability reporting expectations. It is **not** a statement that the application underwent an independent security audit.

## Reporting a vulnerability

Use this repository's GitHub **Security > Report a vulnerability** flow if enabled, or privately contact the maintainer using an authorized channel. Do not include actual personal DNS traffic, user data, signing keys, active exploit payloads or sensitive private details in public GitHub issues. Provide affected commit/version, non-sensitive reproduction steps, expected versus actual behavior, impact, and a minimal synthetic test. No fixed disclosure SLA is promised here.

## Critical attack surfaces

| Surface | Minimum checks | Code |
| --- | --- | --- |
| VPN TUN reader, IP/UDP/TCP/DNS | bounds, compression loops, malformed flags/lengths, checksums, concurrency, packet ownership | `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`, `app/src/main/java/com/unblocker/app/logic/dns/` |
| Resolver forwarding | real network resolver discovery, protect sockets, response tx-id/question matching, UDP truncation/TCP retry, no loop | `app/src/main/java/com/unblocker/app/services/DnsUpstreamClient.kt`, `DnsResolverPolicy.kt`, `ResolverRegistry.kt` |
| Rule lists | HTTPS only, public addresses/redirect checks, download/line/rule caps, syntax admission, integrity/provenance | `BoundedFilterDownloader.kt`, `RuleLineParser.kt`, `tools/filter-compiler/` |
| Domain policy | never-block/essential-service protections, user rule precedence, quarantine, source review | `DecideBlockingUseCase.kt`, `NeverBlockPolicy.kt`, `ProtectedDomains.kt` |
| Local privacy | protect Keystore HMAC, private backup exclusion, bounded Room aggregate logs, clear/pause race | `DeviceLearning.kt`, `PrivateEvidenceStore.kt`, `QueryLogSink.kt`, `data-store/` |
| Android lifecycle | VPN consent, component export, foreground notifications, boot restrictions | `AndroidManifest.xml`, `BootReceiver.kt`, `QuickStartManager.kt` |
| Build/release | signing secret isolation, provenance, native binary gate, immutable signed release artifacts | `.github/workflows/`, `scripts/release-policy.py` |

## Review requirements

- Threat-model affected trust boundaries **before** implementation; introduce synthetic malicious inputs and fail safely.
- For DNS/parser changes test compression pointers, truncation, invalid name sizes, TCP framing, IPv6 extension chains, checksums, spoofed upstream replies and races.
- For source downloads test redirects to loopback/private destinations and DNS rebinding / address-to-connection mismatch; URL validation alone may not pin the actual connection target.
- Reject unverified remote-list additions or licensing ambiguities; never trust downloaded rules as code.
- Store secrets in approved CI environment; never commit keystores, `.env`, `local.properties` or credentials.
- Keep threat findings private until appropriate disclosure. See [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) and [docs/audits/INITIAL_REVIEW_2026-11-13.md](docs/audits/INITIAL_REVIEW_2026-11-13.md).
