# SPEC.md — Project Specification

> **Status**: `FINALIZED`
>
> ⚠️ **Planning Lock**: No code may be written until this spec is marked `FINALIZED`.

## Vision
Harden `un-blocker` against security vulnerabilities, evasion vectors, connection exhaustion DoS, and resource leaks identified in the senior security audit, culminating in release `v2.1.8`.

## Goals
1. **Upstream DNS Transaction ID Randomization (RFC 5452 Compliance, SEC-02)** — Prevent off-path DNS response spoofing and Kaminsky cache poisoning by assigning an unpredictable cryptographically random 16-bit TxID (`SecureRandom`) to every outbound upstream query in `DnsUpstreamClient`, restoring the original client TxID upon delivery.
2. **Direct Public DNS Interception in Split-Tunnel Routing (SEC-01)** — Prevent evasive applications and ad SDKs from bypassing the local VPN by routing major public DNS unicast addresses (`8.8.8.8`, `8.8.4.4`, `1.1.1.1`, `1.0.0.1`, `9.9.9.9`, `9.9.9.10`, `208.67.222.222`, `208.67.220.220`, `2001:4860:4860::8888`, `2606:4700:4700::1111`) through the TUN interface, and processing queries sent to these destination IPs in `UnblockerVpnService`.
3. **TCP DNS Responder LRU Connection Eviction (SEC-03)** — Prevent connection pool exhaustion DoS attacks in `TcpDnsResponder` by replacing hard connection rejections with least-recently-active (LRU) connection eviction when `connections.size >= maxConnections`.
4. **TFLite Model File Descriptor Resource Management (SEC-05)** — Prevent file descriptor leaks in `TFLiteClassifierImpl` by ensuring `FileInputStream` and associated channel allocations are cleanly closed after memory mapping the model buffer.
5. **Release Packaging & Validation (v2.1.8)** — Bump version to `2.1.8` (`versionCode = 26`), pass all unit and regression tests, assemble signed release APKs, push to git, and publish the release on GitHub.

## Constraints
- Target Android SDK 35, minimum SDK 26.
- Strict privacy: No user hostnames, URLs, or client IPs in diagnostics or exception logs.
- Memory & Performance: Non-blocking reader loop; bounded queues; bounded buffers.
- One command per invocation; atomic commits per task.

## Success Criteria
- [x] SPEC finalized and roadmap planned.
- [ ] Upstream DNS queries randomized with cryptographically secure 16-bit TxIDs.
- [ ] Direct public DNS IP destinations trapped and filtered via TUN routing.
- [ ] TCP DNS connection pool implements LRU eviction without dropping new requests.
- [ ] TFLite model loader closes file streams safely.
- [ ] All unit test tasks pass with 100% green status.
- [ ] Version bumped to 2.1.8 (`versionCode = 26`), APKs assembled, and GitHub Release v2.1.8 published.
