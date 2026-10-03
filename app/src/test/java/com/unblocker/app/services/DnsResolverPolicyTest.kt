package com.unblocker.app.services

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsResolverPolicyTest {
    @Test fun removesTunnelLoopbackUnspecifiedAndDuplicateAddresses() {
        val result = DnsResolverPolicy.sanitize(listOf(
            InetAddress.getByName("10.10.0.1"),
            InetAddress.getByName("127.0.0.1"),
            InetAddress.getByName("0.0.0.0"),
            InetAddress.getByName("192.168.1.1"),
            InetAddress.getByName("192.168.1.1"),
            InetAddress.getByName("2001:4860:4860::8888")
        ))

        assertEquals(listOf("192.168.1.1", "2001:4860:4860:0:0:0:0:8888"),
            result.mapNotNull { it.hostAddress })
    }

    @Test fun doesNotInventPublicFallbackWhenNoSystemResolverIsUsable() {
        val result = DnsResolverPolicy.sanitize(listOf(
            InetAddress.getByName("10.10.0.1"),
            InetAddress.getByName("::")
        ))

        assertEquals(emptyList<InetAddress>(), result)
    }

    @Test fun selectConfiguredReturnsOnlySanitizedAndroidResolvers() {
        val result = DnsResolverPolicy.selectConfigured(listOf(
            listOf(
                InetAddress.getByName("10.10.0.1"),
                InetAddress.getByName("192.168.1.1"),
                InetAddress.getByName("192.168.1.1")
            ),
            listOf(
                InetAddress.getByName("::"),
                InetAddress.getByName("2001:db8::53")
            )
        ))

        assertEquals(
            listOf("192.168.1.1", "2001:db8:0:0:0:0:0:53"),
            result.mapNotNull { it.hostAddress }
        )
    }

    @Test fun selectConfiguredFailsClosedWhenDiscoveryIsEmptyOrUnusable() {
        assertEquals(emptyList<InetAddress>(), DnsResolverPolicy.selectConfigured(emptyList()))
        assertEquals(
            emptyList<InetAddress>(),
            DnsResolverPolicy.selectConfigured(listOf(
                listOf(
                    InetAddress.getByName("10.10.0.1"),
                    InetAddress.getByName("fd00:1::1"),
                    InetAddress.getByName("127.0.0.1"),
                    InetAddress.getByName("0.0.0.0")
                )
            ))
        )
    }
}
