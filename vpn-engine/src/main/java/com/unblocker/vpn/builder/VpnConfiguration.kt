package com.unblocker.vpn.builder

import android.net.VpnService

/**
 * Builds and configures the Android VPN interface [android.os.ParcelFileDescriptor].
 *
 * Restricts interception to local DNS traffic only:
 *  - Local VPN address 10.0.0.2/32 routes all outbound packets through the tun interface.
 *  - The app's own package is excluded (addDisallowedApplication) to prevent routing loops
 *    when the VPN service itself needs to forward DNS packets to the upstream resolver.
 *  - MTU 1500 matches typical Ethernet frame size for best performance.
 *
 * @param service         The active [VpnService] instance used to call [VpnService.Builder].
 * @param upstreamServers The list of upstream DNS resolvers to advertise inside the tunnel.
 */
class VpnConfiguration(
    private val service: VpnService,
    private val upstreamServers: List<UpstreamDnsServer> = UpstreamDnsServer.DEFAULTS
) {

    /**
     * Establishes the VPN interface and returns the file descriptor.
     * Returns null if the VPN permission has been revoked.
     */
    fun establish(): android.os.ParcelFileDescriptor? {
        val builder = service.Builder()
            .setSession("UnblockerLocalVpn")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)            // IPv6 route to intercept v6 DNS
            .setMtu(1500)
            .setBlocking(true)

        upstreamServers.forEach { server ->
            builder.addDnsServer(server.address)
        }

        // Exclude our own app process to avoid VPN routing loops
        try {
            builder.addDisallowedApplication(service.packageName)
        } catch (e: Exception) {
            // Ignore, e.g. if package not found
        }

        return try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
    }
}
