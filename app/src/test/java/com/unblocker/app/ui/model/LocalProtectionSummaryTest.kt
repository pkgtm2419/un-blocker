package com.unblocker.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalProtectionSummaryTest {

    @Test
    fun defaultSummaryHasZeroCountsAndNullTimestamps() {
        val summary = LocalProtectionSummary(
            dnsQueries = 0L,
            blockedDnsQueries = 0L,
            firstObservedMs = null,
            lastObservedMs = null
        )
        assertEquals(0L, summary.dnsQueries)
        assertEquals(0L, summary.blockedDnsQueries)
        assertNull(summary.firstObservedMs)
        assertNull(summary.lastObservedMs)
    }

    @Test
    fun populatedSummaryRetainsAccurateCounts() {
        val now = 1700000000000L
        val summary = LocalProtectionSummary(
            dnsQueries = 1500L,
            blockedDnsQueries = 450L,
            firstObservedMs = now - 86400000L,
            lastObservedMs = now
        )
        assertEquals(1500L, summary.dnsQueries)
        assertEquals(450L, summary.blockedDnsQueries)
        assertEquals(now - 86400000L, summary.firstObservedMs)
        assertEquals(now, summary.lastObservedMs)
    }
}
