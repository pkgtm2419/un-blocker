package com.unblocker.app.services

/**
 * Coverage of observable standard system DNS.
 * Never claims full-device anonymity or encrypted DoH interception.
 */
enum class ProtectionCoverage {
    FULL_SYSTEM_DNS,
    LIMITED_PRIVATE_DNS,
    UNKNOWN_RESOLVER,
    OFF;

    companion object {
        fun compute(
            status: ServiceStatus,
            isPrivateDnsActive: Boolean,
            hasUsableResolvers: Boolean
        ): ProtectionCoverage = when {
            status != ServiceStatus.RUNNING -> OFF
            isPrivateDnsActive -> LIMITED_PRIVATE_DNS
            hasUsableResolvers -> FULL_SYSTEM_DNS
            else -> UNKNOWN_RESOLVER
        }
    }
}
