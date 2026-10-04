package com.unblocker.vpn.builder

/**
 * Upstream DNS server configuration.
 *
 * @param address       IP address of the upstream DNS resolver.
 * @param port          DNS port (default 53).
 * @param useTcp        If true, use TCP fallback when UDP response is truncated.
 */
data class UpstreamDnsServer(
    val address: String,
    val port: Int = 53,
    val useTcp: Boolean = true
) {
    companion object {
        /** Cloudflare DNS — low latency, privacy-focused. */
        val CLOUDFLARE = UpstreamDnsServer("1.1.1.1")

        /** Google DNS — reliable fallback. */
        val GOOGLE = UpstreamDnsServer("8.8.8.8")

        /** Ordered list of preferred upstream resolvers. */
        val DEFAULTS = listOf(CLOUDFLARE, GOOGLE)
    }
}
