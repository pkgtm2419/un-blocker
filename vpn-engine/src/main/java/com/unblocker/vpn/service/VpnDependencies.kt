package com.unblocker.vpn.service

/**
 * Defines the external dependencies required by the VPN engine.
 * Implementing this interface allows the data-store module to remain decoupled
 * from the VPN module while avoiding complex Dagger generic/wildcard bindings.
 */
interface VpnDependencies {
    /**
     * Checks whether a [domain] is blocked.
     * Evaluates in O(1) time against an in-memory Bloom Filter.
     */
    fun isBlocked(domain: String): Boolean

    /**
     * Asynchronously records each DNS request to the local Room database.
     */
    suspend fun logDnsRequest(domain: String, blocked: Boolean)

    /**
     * Initializes the Bloom Filter from the database.
     */
    suspend fun initFilter()
}
