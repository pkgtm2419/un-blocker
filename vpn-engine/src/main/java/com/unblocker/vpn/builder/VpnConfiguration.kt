package com.unblocker.vpn.builder

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

/**
 * Builds and configures the Android VPN interface [ParcelFileDescriptor] with dual-stack
 * split-tunnel routing.
 *
 * Restricts interception to local DNS traffic only:
 *  - Local sinkhole addresses: 10.111.222.1/24 (IPv4) and fd00:1:fd00:1:fd00:1:fd00:1/128 (IPv6).
 *  - Specific /32 and /128 routes for DNS servers only, allowing all other traffic to remain
 *    on hardware interfaces with zero latency penalty.
 *  - The app's own package is excluded (addDisallowedApplication) to prevent routing loops.
 *  - Automatic physical network tracking (setUnderlyingNetworks(null)) across Wi-Fi, 5G, and LTE.
 */
class VpnConfiguration(
    private val service: VpnService,
    private val upstreamServers: List<UpstreamDnsServer> = UpstreamDnsServer.DEFAULTS
) {

    /**
     * Establishes the VPN interface and returns the file descriptor.
     * Returns null if the VPN permission has been revoked or initialization fails.
     */
    fun establish(): ParcelFileDescriptor? {
        val builder = service.Builder()
            .setSession("Un-Blocker DNS Shield")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress("10.111.222.1", 24)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)

        // Dual-stack IPv6 DNS intercept (/128)
        try {
            builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            builder.addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
            builder.addRoute("fd00:1:fd00:1:fd00:1:fd00:2", 128)
        } catch (_: Exception) {
            // Graceful fallback on devices without IPv6 support
        }

        // Exclude our own app process to avoid VPN routing loops
        try {
            builder.addDisallowedApplication(service.packageName)
        } catch (_: Exception) {}

        // Automatic Physical Network Tracking (Wi-Fi <-> Cellular)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setUnderlyingNetworks(null)
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        return try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
    }
}
