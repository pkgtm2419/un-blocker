package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsQuery

/**
 * Identity of one in-flight DNS resolution shared by matching requests.
 *
 * @property domain Canonical queried hostname.
 * @property queryType DNS QTYPE wire bits.
 * @property queryClass DNS QCLASS wire bits.
 * @property isIpv6 Whether client transport uses IPv6.
 * @property resolverEpoch Resolver topology generation at submission.
 */
internal data class CoalesceKey(
    val domain: String,
    val queryType: Short,
    val queryClass: Short,
    val isIpv6: Boolean,
    val resolverEpoch: Long,
)

/**
 * Shares a resolution leader with a bounded number of duplicate waiting queries.
 *
 * @param maxWaitersPerKey Maximum duplicate waiters in addition to the leader.
 */
internal class DnsRequestCoalescer(
    private val maxWaitersPerKey: Int = 32,
) {
    private class InFlight(
        val leaderQuery: DnsQuery,
    ) {
        val waiters = ArrayList<DnsQuery>()
    }

    private val inFlight = HashMap<CoalesceKey, InFlight>()
    private val lock = Any()

    /**
     * Outcome of attaching a DNS query to an in-flight resolution.
     */
    sealed class JoinResult {
        /**
         * No matching resolution existed; the caller owns upstream work.
         */
        object Leader : JoinResult()

        /**
         * Query attached to an existing leader for later response delivery.
         */
        object Joined : JoinResult()

        /**
         * Duplicate waiter capacity was exhausted.
         */
        object Overflow : JoinResult()
    }

    /**
     * Registers a new leader or attaches a waiter within the per-key capacity.
     *
     * @param key Query identity and resolver epoch.
     * @param query Query whose packet storage must remain valid until completion.
     * @return Leader, joined or overflow outcome.
     */
    fun attachOrLead(
        key: CoalesceKey,
        query: DnsQuery,
    ): JoinResult =
        synchronized(lock) {
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
     * Removes one resolution and transfers its queries to the caller for delivery.
     *
     * @param key Identity of the completed resolution.
     * @return Leader followed by waiters, or an empty list when no resolution exists.
     */
    fun complete(key: CoalesceKey): List<DnsQuery> =
        synchronized(lock) {
            val flight = inFlight.remove(key) ?: return emptyList()
            val all = ArrayList<DnsQuery>(flight.waiters.size + 1)
            all.add(flight.leaderQuery)
            all.addAll(flight.waiters)
            return all
        }

    /**
     * Number of distinct resolution keys currently tracked.
     */
    val activeInFlightCount: Int get() = synchronized(lock) { inFlight.size }

    /**
     * Removes all pending resolutions.
     *
     * @return All leaders and waiters transferred to the caller without delivering replies.
     */
    fun clear(): List<DnsQuery> =
        synchronized(lock) {
            val remaining = ArrayList<DnsQuery>()
            for (flight in inFlight.values) {
                remaining.add(flight.leaderQuery)
                remaining.addAll(flight.waiters)
            }
            inFlight.clear()
            return remaining
        }
}
