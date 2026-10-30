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

/**
 * Well-known public DNS resolvers trapped into the VPN tunnel
 * to prevent evasive applications from bypassing local DNS filtering.
 */
object TrappedDnsServers {
    val IPV4_TRAPPED: List<String> = listOf(
        "8.8.8.8", "8.8.4.4",               // Google
        "1.1.1.1", "1.0.0.1",               // Cloudflare
        "9.9.9.9", "9.9.9.10",             // Quad9
        "208.67.222.222", "208.67.220.220", // OpenDNS
        "94.140.14.14", "94.140.15.15",     // AdGuard
        "76.76.2.0", "76.76.10.0"           // Control D
    )

    val IPV6_TRAPPED: List<String> = listOf(
        "2001:4860:4860::8888", "2001:4860:4860::8844",   // Google
        "2606:4700:4700::1111", "2606:4700:4700::1001",   // Cloudflare
        "2620:fe::fe", "2620:fe::9",                       // Quad9
        "2620:119:35::35", "2620:119:53::53",             // OpenDNS
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"           // AdGuard
    )
}
