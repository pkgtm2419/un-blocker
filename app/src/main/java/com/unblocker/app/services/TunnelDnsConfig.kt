package com.unblocker.app.services

import java.net.InetAddress

/**
 * Single authoritative source of truth for local VPN DNS tunnel endpoints.
 */
object TunnelDnsConfig {
    const val IPV4_CLIENT = "10.111.222.1"
    const val IPV4_DNS = "10.111.222.2"
    const val IPV6_CLIENT = "fd00:1:fd00:1:fd00:1:fd00:1"
    const val IPV6_DNS = "fd00:1:fd00:1:fd00:1:fd00:2"

    private val addresses: Set<InetAddress> by lazy {
        setOf(
            InetAddress.getByName(IPV4_DNS),
            InetAddress.getByName(IPV6_DNS)
        )
    }

    fun tunnelResolverAddresses(): Set<InetAddress> = addresses
}
