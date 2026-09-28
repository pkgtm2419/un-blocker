package com.unblocker.app.services

import java.net.InetAddress

/** Keeps DNS forwarding on Android-provided upstreams and out of the VPN's own tunnel. */
object DnsResolverPolicy {
    private const val TUNNEL_DNS = "10.10.0.1"
    private const val TUNNEL_DNS_IPV6 = "fd00:1::1"

    fun sanitize(addresses: Iterable<InetAddress>): List<InetAddress> = addresses
        .filterNot { address ->
            address.hostAddress == TUNNEL_DNS || address.hostAddress == TUNNEL_DNS_IPV6 ||
                address.isAnyLocalAddress || address.isLoopbackAddress
        }
        .distinctBy { it.address.toList() }
}
