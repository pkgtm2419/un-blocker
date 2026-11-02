package com.unblocker.app.ui.model

/**
 * Honest, local-only protection summary.
 * Never labels DNS query counts as ads removed, people protected, or bytes saved.
 */
data class LocalProtectionSummary(
    val dnsQueries: Long,
    val blockedDnsQueries: Long,
    val firstObservedMs: Long?,
    val lastObservedMs: Long?
)
