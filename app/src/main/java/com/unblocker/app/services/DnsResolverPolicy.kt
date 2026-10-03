package com.unblocker.app.services

import java.net.InetAddress

/** Keeps DNS forwarding on Android-provided upstreams and out of the VPN's own tunnel. */
object DnsResolverPolicy {
    private val tunnelDnsAddresses = setOf(
        InetAddress.getByName("10.10.0.1").address.toList(),
        InetAddress.getByName("fd00:1::1").address.toList()
    )

    fun sanitize(addresses: Iterable<InetAddress>): List<InetAddress> = addresses
        .filterNot { address ->
            address.address.toList() in tunnelDnsAddresses ||
                address.isAnyLocalAddress || address.isLoopbackAddress
        }
        .distinctBy { it.address.toList() }

    fun selectConfigured(groups: Iterable<Iterable<InetAddress>>): List<InetAddress> =
        sanitize(groups.flatten())
}
