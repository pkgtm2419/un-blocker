package com.unblocker.vpn.builder

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

/**
 * Builds the library DNS-targeted VPN interface using synthetic IPv4 and IPv6 endpoints.
 *
 * Only the synthetic DNS endpoints are routed explicitly. The app package is excluded when
 * possible, and underlying-network tracking is left to Android. This builder is separate from
 * the manifest-declared app service configuration.
 *
 * @param service VPN service owning the builder and established descriptor.
 * @param upstreamServers Retained configuration parameter; currently unused by [establish].
 */
class VpnConfiguration(
    private val service: VpnService,
    private val upstreamServers: List<UpstreamDnsServer> = UpstreamDnsServer.DEFAULTS
) {

    /**
     * Configures and attempts to establish the VPN interface.
     *
     * IPv6, package exclusion, and underlying-network setup failures are individually ignored.
     * The final establish call catches exceptions, but earlier uncaught builder failures propagate.
     *
     * @return Descriptor owned by the caller, or null when the final establishment fails or lacks consent.
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
        } catch (_: Exception) {
            null
        }
    }
}
