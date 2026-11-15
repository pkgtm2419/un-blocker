# DNS/VPN packet lifecycle and trust boundaries

## Packet path

1. Android grants VPN consent; `QuickStartManager` starts the manifest-declared `UnblockerVpnService` foreground service. The service establishes local DNS-targeted routes and owns the TUN file descriptor.
2. TUN reader extracts IP/UDP DNS, validates packet sizes and header fields; DNS-over-TCP requests are handled by the tunnel responder. Source: `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`, `app/src/main/java/com/unblocker/app/logic/dns/DnsPacket.kt`, `TcpDnsResponder.kt`.
3. Domain normalization and policy decision: `ContentFilterEngine` delegates to `DecideBlockingUseCase` with device user rules, static extensions, adaptive evidence, optional adult filter.
4. Block: synthesize an appropriate DNS response locally, honoring query ID, question and transport checksum (including IPv6); never forge traffic to an unrelated endpoint.
5. Allow: use safe TTL cache/coalescing where applicable; obtain only valid, non-VPN Android-network resolvers via `DnsResolverPolicy`/`ResolverRegistry`; call `VpnService.protect()` on upstream sockets to prevent loops.
6. Upstream `DnsUpstreamClient` performs UDP and TCP fallback for truncated UDP responses, validates returned transaction ID/question/type/class via `DnsResponseValidator`, then wraps and writes reply. Network transitions must not forward via stale resolver epochs.
7. `QueryLogSink` receives normalized decisions asynchronously when enabled. Don't make SQLite work block the packet-processing hot path.

## Safety and observability

- Inputs: untrusted TUN packet bytes, DNS compression pointers, network replies, extension lists, user hostnames. Output: DNS wire response and local metadata only.
- Cover IPv4, IPv6, UDP, TCP, fragmentation/extension handling, truncated responses, EDNS flags, unsupported opcodes, circular compression and oversized labels; test negative cases.
- Cache TTL must respect response data, resource caps and invalidation on resolver or policy changes; avoid cross-network/private data leakage.
- The active app uses synthetic endpoints from `TunnelDnsConfig`: `10.111.222.2` and `fd00:1:fd00:1:fd00:1:fd00:2`. These tunnel targets are distinct from Android-network upstream resolvers. Older references to `10.10.0.1`, `fd00:1::1` or `TrappedDnsServers` are historical, not the current endpoint contract.
- DoH in browsers/apps, strict Private DNS, direct-IP connections, third-party tunnels and preexisting caches can bypass or limit DNS filtering. Explain coverage honestly.
- `vpn-engine/resolver/DnsForwarder.kt` separately references `UpstreamDnsServer.DEFAULTS` (Google/Cloudflare). The active app path uses `DnsUpstreamClient`; trace actual entrypoints and Hilt injection before asserting fallback applies to user traffic. Record verified callers.

## Tests to inspect first

`app/src/test/java/com/unblocker/app/dns/{DnsPacketTest,DnsResponseValidatorTest,TcpDnsResponderTest,AdvancedDnsPacketTest,DnsFuzzMutationTest,DnsErrorResponseTest}.kt`, `app/src/test/java/com/unblocker/app/services/{DnsConcurrencyStressTest,DnsRequestCoalescerTest,DnsResolverPolicyTest}.kt`, `app/src/androidTest/java/com/unblocker/app/{LocalPrivacyAndVpnTest,VpnNetworkRegressionTest}.kt`.
