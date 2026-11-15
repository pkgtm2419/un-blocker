package com.unblocker.vpn.service

/**
 * Caller-supplied policy and logging boundary for library VPN implementations.
 */
interface VpnDependencies {
    /**
     * Evaluates the caller-provided domain blocking policy.
     *
     * @param domain Queried domain expected to be normalized by the caller.
     * @return Whether the dependency implementation chooses to block this domain.
     */
    fun isBlocked(domain: String): Boolean

    /**
     * Passes an observation to the dependency implementation's local logging path.
     *
     * @param domain Readable queried domain.
     * @param blocked Blocking decision associated with the observation.
     */
    suspend fun logDnsRequest(
        domain: String,
        blocked: Boolean,
    )

    /**
     * Initializes the implementation-specific filter before it is used.
     */
    suspend fun initFilter()
}
