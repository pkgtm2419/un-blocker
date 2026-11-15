package com.unblocker.app.services

import java.net.InetAddress

/**
 * Single authoritative source of truth for local VPN DNS tunnel endpoints.
 */
object TunnelDnsConfig {
    /**
     * IPv4 address assigned to the local tunnel interface.
     */
    const val IPV4_CLIENT = "10.111.222.1"

    /**
     * Synthetic IPv4 resolver endpoint routed into the tunnel.
     */
    const val IPV4_DNS = "10.111.222.2"

    /**
     * IPv6 address assigned to the local tunnel interface.
     */
    const val IPV6_CLIENT = "fd00:1:fd00:1:fd00:1:fd00:1"

    /**
     * Synthetic IPv6 resolver endpoint routed into the tunnel.
     */
    const val IPV6_DNS = "fd00:1:fd00:1:fd00:1:fd00:2"

    private val addresses: Set<InetAddress> by lazy {
        setOf(
            InetAddress.getByName(IPV4_DNS),
            InetAddress.getByName(IPV6_DNS),
        )
    }

    /**
     * Returns synthetic resolver addresses that must not be selected as upstream resolvers.
     */
    fun tunnelResolverAddresses(): Set<InetAddress> = addresses
}
