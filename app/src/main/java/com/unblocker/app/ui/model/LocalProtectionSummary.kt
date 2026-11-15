package com.unblocker.app.ui.model

/**
 * Local DNS query totals, without inferring advertisements removed or network bytes saved.
 *
 * @property dnsQueries Total recorded DNS queries.
 * @property blockedDnsQueries Recorded queries receiving a block decision.
 * @property firstObservedMs Earliest recorded observation in Unix epoch milliseconds, or null without data.
 * @property lastObservedMs Latest recorded observation in Unix epoch milliseconds, or null without data.
 */
data class LocalProtectionSummary(
    val dnsQueries: Long,
    val blockedDnsQueries: Long,
    val firstObservedMs: Long?,
    val lastObservedMs: Long?
)
