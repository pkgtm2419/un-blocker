package com.unblocker.app.services

/**
 * Coverage of observable standard system DNS.
 * Never claims full-device anonymity or encrypted DoH interception.
 */
enum class ProtectionCoverage {
    FULL_SYSTEM_DNS,
    LIMITED_PRIVATE_DNS,
    UNKNOWN_RESOLVER,
    OFF,
    ;

    /**
     * Derives coverage indicators from current service and Android resolver state.
     */
    companion object {
        /**
         * Computes the coverage indicator without probing traffic.
         *
         * @param status Current VPN lifecycle status.
         * @param isPrivateDnsActive Whether Android reports active Private DNS.
         * @param hasUsableResolvers Whether configured upstream resolvers are available.
         * @return OFF unless running, otherwise the Private DNS or resolver availability indicator.
         */
        fun compute(
            status: ServiceStatus,
            isPrivateDnsActive: Boolean,
            hasUsableResolvers: Boolean,
        ): ProtectionCoverage =
            when {
                status != ServiceStatus.RUNNING -> OFF
                isPrivateDnsActive -> LIMITED_PRIVATE_DNS
                hasUsableResolvers -> FULL_SYSTEM_DNS
                else -> UNKNOWN_RESOLVER
            }
    }
}
