package com.unblocker.app.services

import java.net.InetAddress

/** Keeps DNS forwarding on Android-provided upstreams and out of the VPN's own tunnel. */
object DnsResolverPolicy {
    private val tunnelDnsAddresses: Set<List<Byte>> by lazy {
        (TunnelDnsConfig.tunnelResolverAddresses() + setOf(
            InetAddress.getByName("10.10.0.1"),
            InetAddress.getByName("fd00:1::1")
        )).map { it.address.toList() }.toSet()
    }

    fun sanitize(addresses: Iterable<InetAddress>): List<InetAddress> = addresses
        .filterNot { address ->
            address.address.toList() in tunnelDnsAddresses ||
                address.isAnyLocalAddress || address.isLoopbackAddress
        }
        .distinctBy { it.address.toList() }

    fun selectConfigured(groups: Iterable<Iterable<InetAddress>>): List<InetAddress> =
        sanitize(groups.flatten())
}
