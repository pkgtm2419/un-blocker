package com.unblocker.vpn.builder

/**
 * Resolver configuration for the separate library forwarding implementation.
 *
 * @property address Resolver IP address or hostname; this value is not validated at construction.
 * @property port Resolver transport port, conventionally 53.
 * @property useTcp Compatibility preference field; the current DnsForwarder retries truncated UDP
 * responses over TCP regardless of this value.
 */
data class UpstreamDnsServer(
    val address: String,
    val port: Int = 53,
    val useTcp: Boolean = true,
) {
    /**
     * Public resolver configurations retained for the reusable library path.
     */
    companion object {
        /**
         * Cloudflare public IPv4 resolver configuration.
         */
        val CLOUDFLARE = UpstreamDnsServer("1.1.1.1")

        /**
         * Google public IPv4 resolver configuration.
         */
        val GOOGLE = UpstreamDnsServer("8.8.8.8")

        /**
         * Library resolver order: Cloudflare followed by Google; separate from the active app resolver policy.
         */
        val DEFAULTS = listOf(CLOUDFLARE, GOOGLE)
    }
}

/**
 * Well-known resolver address lists retained for callers that explicitly configure interception.
 *
 * Declaring these lists does not add routes or provide coverage for encrypted DNS.
 */
object TrappedDnsServers {
    /**
     * Public IPv4 resolver addresses available to route-building callers.
     */
    val IPV4_TRAPPED: List<String> =
        listOf(
            "8.8.8.8",
            "8.8.4.4", // Google
            "1.1.1.1",
            "1.0.0.1", // Cloudflare
            "9.9.9.9",
            "9.9.9.10", // Quad9
            "208.67.222.222",
            "208.67.220.220", // OpenDNS
            "94.140.14.14",
            "94.140.15.15", // AdGuard
            "76.76.2.0",
            "76.76.10.0", // Control D
        )

    /**
     * Public IPv6 resolver addresses available to route-building callers.
     */
    val IPV6_TRAPPED: List<String> =
        listOf(
            "2001:4860:4860::8888",
            "2001:4860:4860::8844", // Google
            "2606:4700:4700::1111",
            "2606:4700:4700::1001", // Cloudflare
            "2620:fe::fe",
            "2620:fe::9", // Quad9
            "2620:119:35::35",
            "2620:119:53::53", // OpenDNS
            "2a10:50c0::ad1:ff",
            "2a10:50c0::ad2:ff", // AdGuard
        )
}
