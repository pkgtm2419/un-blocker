# VPN DNS Routing and Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the DNS-only VPN intercept exactly its synthetic DNS endpoints without breaking unrelated IP connections, and restore reliable Android instrumentation coverage.

**Architecture:** Centralize tunnel endpoints in one production configuration object, remove public DNS host routes, and have the service and device tests consume the same source of truth. The app remains DNS-only; encrypted or hardcoded resolver bypass is shown honestly to the user rather than implemented with an unsafe partial full tunnel.

**Tech Stack:** Kotlin, Android VpnService, JUnit, AndroidJUnit4, ConnectivityManager, Gradle 9 / AGP 8.10.1 as currently configured.

**Spec:** `docs/superpowers/specs/2026-11-02-unblocker-lightweight-reliability-design.md`

## Global Constraints

- Base SHA: `6fd537e34773e3f799e3685f68a0fbef761eb71c`, Android minSdk 26, targetSdk 35, Java 17, Kotlin 2.0.21.
- Do not route all device traffic; no claim to block DoH, DoT, direct IP connections or non-DNS packets.
- Do not replace public upstream DNS with developer-controlled servers. Continue using validated Android network resolvers and protected upstream sockets.
- Preserve a successful user stop action, denied consent, revoke handling and background boot policy. Unit tests are not a substitute for a real Android TUN test.
- Implementation and each commit require a clean isolated development branch/worktree. **Do not merge or publish before real device verification.**

## Review Focus

1. HTTPS to `1.1.1.1:443` must not be trapped solely because it is a public DNS IP (Task 2).
2. UDP/53 to the advertised local VPN DNS endpoint must still be intercepted (Task 3).
3. Android Private DNS strict mode must show limited coverage, not falsely claim full protection (Task 4).
4. IPv6-only network and Wi-Fi to LTE transitions must not retain stale resolvers (Task 4).
5. Permission denied, `onRevoke()` and rapid start/stop must leave one consistent session state (Task 3).

---

### Task 1: Centralize tunnel addresses and repair stale tests/status

**Problem:** `UnblockerVpnService.kt` now uses `10.111.222.2` and `fd00:1:fd00:1:fd00:1:fd00:2`; `AgentStatusPanel.kt` and `LocalPrivacyAndVpnTest.kt` still use `10.10.0.1` / `fd00:1::1`.

**Files:**
- Create: `app/src/main/java/com/unblocker/app/services/TunnelDnsConfig.kt`.
- Modify: `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt:170-205`.
- Modify: `app/src/main/java/com/unblocker/app/services/DnsResolverPolicy.kt`.
- Modify: `app/src/main/java/com/unblocker/app/ui/components/AgentStatusPanel.kt`.
- Modify: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt`.
- Test: create `app/src/test/java/com/unblocker/app/services/TunnelDnsConfigTest.kt`.

**Interfaces:** Expose `TunnelDnsConfig.IPV4_CLIENT = "10.111.222.1"`, `IPV4_DNS = "10.111.222.2"`, `IPV6_CLIENT = "fd00:1:fd00:1:fd00:1:fd00:1"`, `IPV6_DNS = "fd00:1:fd00:1:fd00:1:fd00:2"`; `fun tunnelResolverAddresses(): Set<InetAddress>`. Prefer immutable constants and values; tests should assert published routes rather than invent hardcoded test addresses.

- [ ] Write `TunnelDnsConfigTest` asserting the exact four endpoint strings and that `DnsResolverPolicy.sanitize()` excludes both tunnel resolver addresses and loopback/unspecified addresses.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.unblocker.app.services.TunnelDnsConfigTest'`; expect test to fail until config is implemented.
- [ ] Create the single configuration and replace duplicated literal addresses in the active service, resolver policy and status panel; update Android tests to look for the advertised endpoint.
- [ ] Rerun focused JVM tests; expect zero failures. Inspect `grep -R '10\.10\.0\.1\|fd00:1::1' app/src/main` and update stale active behavior/comments (retain only explicit historical references if documented).
- [ ] Commit `fix(vpn): unify DNS tunnel endpoints and update tests`.

### Task 2: Stop capturing public DNS IP host routes

**Problem:** `VpnService.Builder.addRoute("1.1.1.1", 32)` and similar entries capture all protocol traffic to those IPs. The partial VPN loop cannot forward arbitrary TCP/HTTPS packets. An existing instrumentation test expects these routes to be absent.

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt:179-205`.
- Modify: `vpn-engine/src/main/java/com/unblocker/vpn/builder/VpnConfiguration.kt:28-72` (remove duplicate/unsafe setup if used; do not leave two conflicting policies).
- Test: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt:vpnDoesNotPublishPublicResolverHostRoutes`.
- Test: create `app/src/test/java/com/unblocker/app/services/VpnRoutePolicyTest.kt`.

**Interfaces:** New `object VpnRoutePolicy { fun hostRoutes(): List<Pair<String,Int>> }` returns only synthetic IPv4/IPv6 DNS endpoints with `/32` or `/128`. The production Builder consumes this list. Do **not** advertise a false port-scoped interception feature.

- [ ] Write `VpnRoutePolicyTest` asserting public resolver addresses (`1.1.1.1`, `8.8.8.8`, `9.9.9.9`, IPv6 counterparts) are absent and only local endpoints remain.
- [ ] Run the focused test and verify it fails against the existing route set.
- [ ] Replace route building in the active service; align or deprecate `VpnConfiguration` so unused duplicates cannot regress later.
- [ ] Run `./gradlew :app:testDebugUnitTest`; expect pass after change.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` on API 26 and API 35 devices; expected: host-route test passes, DNS protection still works and generic HTTPS connectivity to known resolver-host IPs does not break. Collect LinkProperties/route screenshots/logcat on any failure.
- [ ] Commit `fix(vpn): route synthetic DNS endpoints only`.

### Task 3: Real lifecycle and packet acceptance on Android devices

**Files:**
- Modify: `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt`.
- Create: `app/src/androidTest/java/com/unblocker/app/VpnNetworkRegressionTest.kt`.
- Review: `app/src/main/java/com/unblocker/app/services/TunnelRun.kt`, `TunResponseWriter.kt`, `VpnSessionState.kt`, `BootReceiver.kt`.

**Interfaces:** Tests call `VpnService.prepare(context)`, `UnblockerVpnService.start/stop`, `awaitState`, `awaitVpnRouting`, and direct socket requests to `TunnelDnsConfig.IPV4_DNS`. Add device-test-only fixture for building controlled A/AAAA query/response pairs.

- [ ] Add failing test: `blockedDomainReceivesLocalDnsResponseWithoutUpstream()` queries a known blocked domain via the advertised TUN DNS host and asserts the response ID, blocked result and service state.
- [ ] Add test: `permissionRevokedClosesTunnelAndDisablesProtection()` asserts no stale `RUNNING` indication after revoke.
- [ ] Add test: `twentyRapidStartStopCyclesLeaveNoStaleSession()` asserts one active session at most and a clean STOPPED state.
- [ ] Run the new tests on an emulator; capture concrete failures before applying any production fix.
- [ ] Make minimal lifecycle fixes only if tests fail, preserving protected upstream socket and response writer ownership; rerun all device tests.
- [ ] Commit `test(vpn): enforce live tunnel lifecycle and traffic semantics`.

### Task 4: Resolver transitions, IPv6 and honest bypass status

**Files:**
- Modify: `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt:55-93,502-525`.
- Modify: `app/src/main/java/com/unblocker/app/services/ResolverRegistry.kt` if tests show stale epoch handling.
- Modify: `app/src/main/java/com/unblocker/app/ui/components/AgentStatusPanel.kt`.
- Test: `app/src/test/java/com/unblocker/app/services/ResolverRegistryTest.kt`, `DnsResolverPolicyTest.kt`.
- Test: add `app/src/androidTest/java/com/unblocker/app/VpnNetworkRegressionTest.kt` network-switch cases.

**Interfaces:** Introduce a read-only `ProtectionCoverage` enum: `FULL_SYSTEM_DNS`, `LIMITED_PRIVATE_DNS`, `UNKNOWN_RESOLVER`, `OFF` (coverage of **observable standard system DNS only**; never call it all-traffic protection). Computed from current running state and observed resolver/Private DNS data.

- [ ] Write test for resolver loss/renewal: stale network resolver epochs cannot forward after `onLost`; a validated new network restores allowed DNS.
- [ ] Write device test for IPv4/IPv6 DNS paths and Wi-Fi/mobile transition; annotate unavailable test network types as BLOCKED, not PASS.
- [ ] Introduce coverage enum and display actionable warning for strict Private DNS/known bypass; do not promise interception of app-specific DoH.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` and manual Settings/Private DNS matrix on two Android versions.
- [ ] Commit `feat(vpn): expose accurate coverage and survive resolver changes`.

## Exit gates

- No public resolver `/32` or `/128` routes in built VPN; non-DNS Internet traffic is not trapped due to known resolver IPs.
- TUN DNS blocked, allowed, TCP fallback and IPv6 use cases pass on actual Android.
- Consent/revoke, stop/restart and resolver transitions pass; one reproducible test result per supported API profile.
- No user-visible stale tunnel address or false claim of encrypted DNS interception.
- Record end-to-end before/after metrics and commit each task separately. Then seek review of the next plan.
