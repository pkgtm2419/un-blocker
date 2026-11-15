package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

/**
 * Regression coverage for DNS resolver policy.
 */
class DnsResolverPolicyTest {
    /**
     * Verifies that removes tunnel loopback unspecified and duplicate addresses.
     */
    @Test fun removesTunnelLoopbackUnspecifiedAndDuplicateAddresses() {
        val result =
            DnsResolverPolicy.sanitize(
                listOf(
                    InetAddress.getByName("10.10.0.1"),
                    InetAddress.getByName("127.0.0.1"),
                    InetAddress.getByName("0.0.0.0"),
                    InetAddress.getByName("192.168.1.1"),
                    InetAddress.getByName("192.168.1.1"),
                    InetAddress.getByName("2001:4860:4860::8888"),
                ),
            )

        assertEquals(
            listOf("192.168.1.1", "2001:4860:4860:0:0:0:0:8888"),
            result.mapNotNull { it.hostAddress },
        )
    }

    /**
     * Verifies that does not invent public fallback when no system resolver is usable.
     */
    @Test fun doesNotInventPublicFallbackWhenNoSystemResolverIsUsable() {
        val result =
            DnsResolverPolicy.sanitize(
                listOf(
                    InetAddress.getByName("10.10.0.1"),
                    InetAddress.getByName("::"),
                ),
            )

        assertEquals(emptyList<InetAddress>(), result)
    }

    /**
     * Verifies that select configured returns only sanitized android resolvers.
     */
    @Test fun selectConfiguredReturnsOnlySanitizedAndroidResolvers() {
        val result =
            DnsResolverPolicy.selectConfigured(
                listOf(
                    listOf(
                        InetAddress.getByName("10.10.0.1"),
                        InetAddress.getByName("192.168.1.1"),
                        InetAddress.getByName("192.168.1.1"),
                    ),
                    listOf(
                        InetAddress.getByName("::"),
                        InetAddress.getByName("2001:db8::53"),
                    ),
                ),
            )

        assertEquals(
            listOf("192.168.1.1", "2001:db8:0:0:0:0:0:53"),
            result.mapNotNull { it.hostAddress },
        )
    }

    /**
     * Verifies that select configured fails closed when discovery is empty or unusable.
     */
    @Test fun selectConfiguredFailsClosedWhenDiscoveryIsEmptyOrUnusable() {
        assertEquals(emptyList<InetAddress>(), DnsResolverPolicy.selectConfigured(emptyList()))
        assertEquals(
            emptyList<InetAddress>(),
            DnsResolverPolicy.selectConfigured(
                listOf(
                    listOf(
                        InetAddress.getByName("10.10.0.1"),
                        InetAddress.getByName("fd00:1::1"),
                        InetAddress.getByName("127.0.0.1"),
                        InetAddress.getByName("0.0.0.0"),
                    ),
                ),
            ),
        )
    }
}
