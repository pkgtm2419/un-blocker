package com.unblocker.app.services

import java.net.InetAddress

/** Keeps DNS forwarding on Android-provided upstreams and out of the VPN's own tunnel. */
object DnsResolverPolicy {
    // Retain legacy tunnel addresses to sanitize historical resolver references
    private val legacyTunnelDnsAddresses = setOf(
        InetAddress.getByName("10.10.0.1"),
        InetAddress.getByName("fd00:1::1")
    )

    private val tunnelDnsAddresses: Set<List<Byte>> by lazy {
        (TunnelDnsConfig.tunnelResolverAddresses() + legacyTunnelDnsAddresses)
            .map { it.address.toList() }
            .toSet()
    }

    /**
     * Removes synthetic tunnel, unspecified and loopback resolver addresses, then deduplicates by bytes.
     *
     * @param addresses Android-configured resolver candidates; private network addresses remain eligible.
     * @return Usable distinct candidates in input order, without adding a public fallback.
     */
    fun sanitize(addresses: Iterable<InetAddress>): List<InetAddress> = addresses
        .filterNot { address ->
            address.address.toList() in tunnelDnsAddresses ||
                address.isAnyLocalAddress || address.isLoopbackAddress
        }
        .distinctBy { it.address.toList() }

    /**
     * Flattens and sanitizes network-provided resolver groups.
     *
     * @param groups Resolver lists in network preference order.
     * @return Sanitized configured resolvers without injecting defaults.
     */
    fun selectConfigured(groups: Iterable<Iterable<InetAddress>>): List<InetAddress> =
        sanitize(groups.flatten())
}
