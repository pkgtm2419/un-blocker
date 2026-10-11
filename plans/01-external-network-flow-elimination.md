# 01 - External Network Flow Elimination & Zero-Leak Audit

## Goal
Guarantee that Un-Blocker performs 100% of its domain analysis, rule evaluation, and network decisions entirely on-device without any outgoing telemetry, API requests, third-party tracker SDKs, or cloud sync. Ensure Android network callbacks (`NetworkRequest`) remain strictly diagnostic/reactive without generating traffic.

## Current State
- The VPN implementation captures DNS traffic targeting local loopback/virtual IPs (`10.10.0.1:53` and `fd00:1::1:53`).
- Upstream resolution is delegated exclusively to Android system-configured network resolvers via `DnsUpstreamClient` when allowed.
- `UnblockerVpnService.kt` registers a `NetworkCallback` using `NetworkRequest.Builder()` to detect network availability and default network changes.
- The app uses zero HTTP/REST clients (no Retrofit, OkHttp, Volley, Ktor, or java.net.HttpURLConnection).
- `usesCleartextTraffic="false"` is enforced in `AndroidManifest.xml`.

## Steps
1. Audit all occurrences of `android.net.NetworkRequest` and `ConnectivityManager` callbacks in `UnblockerVpnService.kt` to ensure callbacks only listen for network state and do not initiate any network connections.
2. Confirm that `DnsUpstreamClient` only forwards user-initiated DNS queries to system resolvers and never phones home.
3. Validate that `DeviceLearning`, `LocalNetworkLearner`, and `PrivateEvidenceStore` strictly persist data locally in app-private files (`noBackupFilesDir`) and never export evidence over the network.
4. Add automated unit/lint checks verifying no outbound HTTP client classes or socket connections to external telemetry endpoints are present in the classpath.
5. Add explicit inline architectural assertions and documentation confirming zero external network flows.

## Acceptance Criteria
- Codebase contains zero references to remote analytics, crash reporting endpoints, or remote ad lists downloads at runtime.
- Network callbacks in `UnblockerVpnService` are strictly listener-only.
- All DNS queries are either sinkholed on-device or forwarded solely to system-configured DNS resolvers.
- Automated tests pass with zero network dependency.

## Risks & Dependencies
- System private DNS (DoT) detection must remain functional via Android system APIs (`LinkProperties`) without triggering outbound network probing.
