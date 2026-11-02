package com.unblocker.app.services

/**
 * Route policy for the DNS-only split-tunnel VPN.
 * Only advertises synthetic tunnel DNS endpoints to ensure non-DNS traffic
 * to public DNS servers (like HTTPS to 1.1.1.1:443) is never intercepted or dropped.
 */
object VpnRoutePolicy {
    fun hostRoutes(): List<Pair<String, Int>> = listOf(
        TunnelDnsConfig.IPV4_DNS to 32,
        TunnelDnsConfig.IPV6_DNS to 128
    )
}
