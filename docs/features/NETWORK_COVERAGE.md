# DNS interception coverage and known bypasses

**Evidence status:** Source-observed at target `main` `8934519` (2026-11-10), not a successful emulator run.

## Code owners

- `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`
- `app/src/main/java/com/unblocker/app/services/DnsResolverPolicy.kt`
- `app/src/main/java/com/unblocker/app/ui/model/ProtectionUiStateMapper.kt`
- `vpn-engine/src/main/java/com/unblocker/vpn/builder/UpstreamDnsServer.kt`

## Current functional contract and verification

Only DNS queries traversing local VPN interception can be filtered. Browser/app-configured DoH, Android strict Private DNS, direct IP calls, cached DNS, ads sharing a domain with content, OEM restrictions and competing VPNs reduce coverage. Android network resolvers receive allowed DNS and may observe domains; this is not anonymity.

**Regressions:** strict Private DNS status banner, no unusable resolver forwarding, no generic public default in active app path, TCP fallback, IPv6, Wi-Fi↔mobile changes, captive portals. Distinguish the reusable `:vpn-engine` public default definitions from actual active app code by caller tracing.

## Maintaining this page

When behavior changes: state before/after, affected permissions/data, acceptance criteria, specific tests executed (or NOT RUN), limitations and release impact. Update [feature matrix](../FEATURE_MATRIX.md), [behavior contracts](../BEHAVIOR_CONTRACTS.md) where applicable, and machine indexes (`python3 scripts/docs_sync.py --write`).
