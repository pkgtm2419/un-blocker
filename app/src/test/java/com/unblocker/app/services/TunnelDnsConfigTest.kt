package com.unblocker.app.services

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelDnsConfigTest {
    @Test
    fun assertsExactFourEndpointStrings() {
        assertEquals("10.111.222.1", TunnelDnsConfig.IPV4_CLIENT)
        assertEquals("10.111.222.2", TunnelDnsConfig.IPV4_DNS)
        assertEquals("fd00:1:fd00:1:fd00:1:fd00:1", TunnelDnsConfig.IPV6_CLIENT)
        assertEquals("fd00:1:fd00:1:fd00:1:fd00:2", TunnelDnsConfig.IPV6_DNS)
    }

    @Test
    fun tunnelResolverAddressesContainsExpectedResolvers() {
        val addrs = TunnelDnsConfig.tunnelResolverAddresses().map { it.hostAddress }
        assertTrue(addrs.contains("10.111.222.2"))
        assertTrue(addrs.any { it?.contains("fd00:1:fd00:1:fd00:1:fd00:2") == true })
    }

    @Test
    fun dnsResolverPolicySanitizeExcludesTunnelAndLoopback() {
        val inputs = listOf(
            InetAddress.getByName(TunnelDnsConfig.IPV4_DNS),
            InetAddress.getByName(TunnelDnsConfig.IPV6_DNS),
            InetAddress.getByName("10.10.0.1"),
            InetAddress.getByName("fd00:1::1"),
            InetAddress.getByName("127.0.0.1"),
            InetAddress.getByName("0.0.0.0"),
            InetAddress.getByName("::"),
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("8.8.8.8")
        )
        val sanitized = DnsResolverPolicy.sanitize(inputs)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), sanitized.map { it.hostAddress })
    }
}
