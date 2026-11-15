# `:vpn-engine` — reusable VPN/DNS components

**Source snapshot:** `main` `8934519` (2026-11-10); validate against checkout.

`builder/VpnConfiguration.kt`, `builder/UpstreamDnsServer.kt`, `parser/{DnsPacketParser,DnsPacketEngine,IpPacket,UdpPacket}.kt`, `resolver/DnsForwarder.kt`, and `service/{NetworkChangeReceiver,VpnStateHolder,VpnDependencies}.kt` provide alternate/reusable infrastructure.

**Security caveat:** `UpstreamDnsServer.DEFAULTS` defines Cloudflare/Google servers. This is a *source-observed default within this module*, not proof that active `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt` uses it. Trace Hilt bindings and calls before claiming upstream behavior. New code must never silently introduce hardcoded fallback into active resolution.

**Test:** `vpn-engine/src/test/java/com/unblocker/vpn/parser`, app DNS/network tests and emulator VPN flows. Verify synthetic routed addresses, checksums, protected sockets and IPv4/v6 parity.

## Module rules

Keep dependencies consistent with `settings.gradle.kts`, use checked module build files, test existing contracts before changes, update `docs/PROJECT_MODULES.md`, source-controlled tests, feature matrix and generated symbol/module inventory. Record risk and verification in PR. [Module index](README.md) | [Architecture](../ARCHITECTURE.md).
