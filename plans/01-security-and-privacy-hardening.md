# Plan 01: Security and Privacy Hardening

## Objective

Improve the application's security without changing its privacy-first operating model or breaking the DNS engine.

### SEC-01: Establish a threat model

**Priority:** P0

Threat categories:
- Malformed or hostile DNS packets.
- Malicious DNS responses.
- Forged upstream responses and alias poisoning.
- Malicious or compromised applications on the device.
- Unexpected network changes.
- Corrupted local settings or reputation files.
- Compromised third-party dependencies.
- Unauthorized access to private application data.
- Accidental disclosure through logs or crash reports.
- Tampered or incorrectly signed release artifacts.

**Acceptance criteria**
- Every network-facing component has an identified trust boundary.
- Every persisted data type has a documented protection strategy.
- All critical findings have an owner and a remediation test.
- No claim of complete security is made solely from passing unit tests.

### SEC-02: Harden DNS packet processing

**Priority:** P0

Required checks:
1. Validate packet lengths before reading fields.
2. Validate DNS header fields and question counts.
3. Bound the number of questions, records, and compression-pointer traversals.
4. Reject invalid compression pointers and out-of-bounds offsets.
5. Prevent integer overflow during length calculations.
6. Validate upstream responses against the corresponding outstanding query.
7. Reject unexpected source addresses and ports.
8. Validate response identifiers and relevant question fields.
9. Bound response sizes and parser resource consumption.
10. Ensure malformed responses cannot poison persistent learning or caches.

### SEC-03: Review VPN routing and lifecycle

**Priority:** P0

Verify:
- VPN consent is respected.
- Protection state is not reported as running before establishment succeeds.
- Repeated start and stop requests are handled safely.
- Shutdown closes sockets, file descriptors, and coroutine scopes.
- Old VPN sessions cannot modify the state of a newer session.
- Resolver sockets are protected from routing back through the VPN.
- Wi-Fi-to-mobile and mobile-to-Wi-Fi transitions are handled correctly.
- Network changes invalidate stale resolver and cache state where necessary.
- Revoked consent and failed establishment produce truthful status.
- Boot restoration follows saved user intent and Android restrictions.

### SEC-04: Protect local data

**Priority:** P0

Required work:
- Verify HMAC identifiers are generated using device-specific Keystore key.
- Confirm raw domains are not persisted in reputation or rule snapshots.
- Bound snapshot size and entry counts before parsing.
- Validate schema versions and reject unsupported records safely.
- Test truncated, corrupted, and unexpectedly large files.
- Use atomic replacement for snapshots.
- Exclude private data from Android backup.

### SEC-05: Audit dependencies and Android components

**Priority:** P1

Review:
- Gradle dependency versions.
- Android manifest permissions (only necessary permissions declared).
- Exported activities, services, receivers set to exported=false unless required.
- Cleartext traffic disabled.

### SEC-06: Eliminate accidental information disclosure

**Priority:** P0

- No plaintext DNS queries or domains in production logs.
- No analytics or telemetry integrations.
- Local-only diagnostic displays.
