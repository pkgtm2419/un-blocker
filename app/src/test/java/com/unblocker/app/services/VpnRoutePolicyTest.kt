package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRoutePolicyTest {
    @Test
    fun assertsPublicResolverAddressesAreAbsentAndOnlyLocalEndpointsRemain() {
        val routes = VpnRoutePolicy.hostRoutes()
        val routedHosts = routes.map { it.first }.toSet()

        // Synthetic endpoints must be present
        assertTrue(routedHosts.contains(TunnelDnsConfig.IPV4_DNS))
        assertTrue(routedHosts.contains(TunnelDnsConfig.IPV6_DNS))
        assertEquals(2, routes.size)

        // Public resolvers must NOT be routed
        val forbidden = setOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "9.9.9.10",
            "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15",
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2606:4700:4700::1111", "2606:4700:4700::1001"
        )
        for (ip in forbidden) {
            assertFalse(
                "Public resolver IP $ip must not be captured in DNS-only VPN routes",
                routedHosts.contains(ip)
            )
        }
    }
}
