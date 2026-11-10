# Long-Running VPN Reliability Report

**Document ID:** `VPN-RELIABILITY-001`  
**Target Application:** Un-Blocker (`com.unblocker.app`) v2.4.1  
**Test Platform:** Android 17 (API 37, x86_64)  
**Audit Date:** 2026-11-08  
**Scope:** Lifecycle Transitions, Interface Teardown, Network Switching, and Fault Recovery

---

## 1. Executive Summary

A comprehensive reliability audit of the Android `VpnService` lifecycle in Un-Blocker v2.4.1 was performed. The service was subjected to abrupt terminations, rapid restarts, background state transitions, screen-off sleep cycles, and network interface drops.

**Key Finding:** The underlying VPN service layer (`UnblockerVpnService`) is exceptionally robust. It never crashed, leaked file descriptors, or threw unhandled `IllegalStateException` or `DeadObjectException` errors during any lifecycle transition. The single-writer `TunResponseWriter` cleanly binds and unbinds from the `/dev/tun` descriptor. However, a significant operational edge case exists on Android 14+ regarding background Doze sleep, where uncached DNS lookups time out when the screen is powered off unless the user manually grants battery optimization exemption.

---

## 2. Itemized Lifecycle Stress Testing

| Lifecycle Event / Scenario | Test Method | Observed Behavioral Evidence | Reliability Status |
| :--- | :--- | :--- | :--- |
| **VPN Startup** | Initial Protection Start | `tun0` interface created: IPv4 `10.111.222.1/24`, IPv6 `fd00:1:fd00:1:fd00:1:fd00:1/128`, MTU 1500. DNS routed to `10.111.222.2`. | **PASS (Clean Startup)** |
| **VPN Shutdown** | In-app UI Toggle | `tun0` interface unmounted instantly. Process returns to idle state without error. | **PASS (Clean Teardown)** |
| **Repeated Restarts** | 5 rapid Start/Stop cycles | Memory remains constant (36 MB – 42 MB). Zero socket leaks or descriptor exhaustion. | **PASS (Leak-Free)** |
| **Background Operation** | HOME key press (`keyevent 3`) | Process transitions to background. Local blocked domains continue to resolve to `0.0.0.0` with 100% success. | **PASS (Background Active)** |
| **Device Screen Off** | POWER key press (`keyevent 26`) | Local sinkholing functions continuously. Uncached upstream queries time out after 2,000 ms due to OS socket power management. | **CONDITIONAL (Doze Constraint)** |
| **Wi-Fi Disconnection / Drop**| Network interface drop | `networkResolvers.snapshot()` detects 0 resolvers, falls back to SERVFAIL without crashing the VPN loop. | **PASS (Graceful Fallback)** |
| **Airplane Mode** | System radio disable | Tunnel remains bound, packet reader pauses on empty stream. Recovers immediately upon radio restoration. | **PASS (Zero Crash)** |
| **DNS Resolver Failure** | Mock upstream blackhole | `resolveAndForward` catches timeout, yields SERVFAIL to client, increments `UPSTREAM_TIMEOUT` counter. | **PASS (Resilient)** |
| **Process Force-Kill** | `am force-stop` | Android kernel tears down `tun0` interface within 50 ms. Network immediately restores to native hardware interface. | **PASS (Fail-Safe)** |
| **Process Relaunch** | Cold restart post-kill | UI reflects disconnected state, cleanly requests user re-engagement without stale socket artifacts. | **PASS (Idempotent)** |

---

## 3. Detailed Architectural Observations

### 3.1 Single-Writer TUN Architecture (`TunResponseWriter`)
Legacy versions of Un-Blocker suffered ANRs and deadlocks caused by multiple coroutine workers attempting concurrent writes to the single Linux `/dev/tun` file descriptor.
In v2.4.1, all packet writes are strictly serialized:
* An `ArrayBlockingQueue<ByteArray>(128)` decouples the packet reader and forwarding workers from the writer thread.
* A single dedicated daemon thread (`TunResponseWriter`) pulls packets and executes `outputStream.write(packet)`.
* In the event of a queue overflow during extreme bursts, the writer discards packets rather than stalling the network thread.

### 3.2 The Android 14+ Doze Mode / Background Network Edge Case
* Under Android 14+ (Target SDK 35), the OS enforces strict background execution limits.
* Un-Blocker declares `android:foregroundServiceType="specialUse"` in its manifest.
* When the screen is turned off for extended intervals, the Android kernel suspends network access for non-system foreground services.
* **Impact:** While local rules (ad blocking) continue to function instantaneously in memory, any app requesting a new, uncached domain while the device screen is off will encounter a DNS timeout until the screen is illuminated.
* **Mitigation Recommendation:** The app should guide users to disable battery optimizations (`Settings -> Apps -> Un-Blocker -> Battery -> Unrestricted`).

---

## 4. Conclusion
The VPN engine in Un-Blocker v2.4.1 is architecturally sound, leak-free, and handles network transitions gracefully. The only functional caveat is Android's OS-level background socket restriction during deep sleep.
