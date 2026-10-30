package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsQuery

/**
 * Key identifying identical in-flight DNS resolution requests.
 * Distinguishes by domain name, query type, query class, IPv6 transport mode, and resolver epoch.
 */
internal data class CoalesceKey(
    val domain: String,
    val queryType: Short,
    val queryClass: Short,
    val isIpv6: Boolean,
    val resolverEpoch: Long
)

/**
 * Coalesces duplicate in-flight DNS forwarding queries into a single upstream resolution.
 *
 * Under bursty client traffic (e.g. parallel browser connections or app pre-connects requesting
 * the same domain concurrently), this eliminates redundant socket I/O, prevents executor queue
 * saturation, reduces mobile radio transmission, and returns responses simultaneously to all
 * waiting queries.
 */
internal class DnsRequestCoalescer(
    private val maxWaitersPerKey: Int = 32
) {
    private class InFlight(val leaderQuery: DnsQuery) {
        val waiters = ArrayList<DnsQuery>()
    }

    private val inFlight = HashMap<CoalesceKey, InFlight>()
    private val lock = Any()

    sealed class JoinResult {
        object Leader : JoinResult()
        object Joined : JoinResult()
        object Overflow : JoinResult()
    }

    /**
     * Checks if a query for [key] is already in flight.
     * If not, registers this query as the [JoinResult.Leader].
     * If already in flight and within [maxWaitersPerKey], attaches to the leader as [JoinResult.Joined].
     * If capacity for this key is exceeded, returns [JoinResult.Overflow].
     */
    fun attachOrLead(key: CoalesceKey, query: DnsQuery): JoinResult = synchronized(lock) {
        val existing = inFlight[key]
        if (existing == null) {
            inFlight[key] = InFlight(query)
            return JoinResult.Leader
        }
        if (existing.waiters.size < maxWaitersPerKey) {
            existing.waiters.add(query)
            return JoinResult.Joined
        }
        return JoinResult.Overflow
    }

    /**
     * Completes resolution for [key] and returns all waiting queries (leader + attached waiters).
     * Removes the key from the in-flight map.
     */
    fun complete(key: CoalesceKey): List<DnsQuery> = synchronized(lock) {
        val flight = inFlight.remove(key) ?: return emptyList()
        val all = ArrayList<DnsQuery>(flight.waiters.size + 1)
        all.add(flight.leaderQuery)
        all.addAll(flight.waiters)
        return all
    }

    /**
     * Returns the number of currently active in-flight resolution keys.
     */
    val activeInFlightCount: Int get() = synchronized(lock) { inFlight.size }

    /**
     * Clears all pending resolutions, typically called during VPN tunnel teardown.
     */
    fun clear(): List<DnsQuery> = synchronized(lock) {
        val remaining = ArrayList<DnsQuery>()
        for (flight in inFlight.values) {
            remaining.add(flight.leaderQuery)
            remaining.addAll(flight.waiters)
        }
        inFlight.clear()
        return remaining
    }
}
