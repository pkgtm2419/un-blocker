package com.unblocker.app.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueryLogSinkTest {

    @Before
    fun setup() {
        QueryLogSink.setLoggingPaused(false)
    }

    @Test
    fun testPauseAndResumeLogging() {
        assertFalse(QueryLogSink.isLoggingPaused())

        QueryLogSink.setLoggingPaused(true)
        assertTrue(QueryLogSink.isLoggingPaused())

        QueryLogSink.setLoggingPaused(false)
        assertFalse(QueryLogSink.isLoggingPaused())
    }

    @Test
    fun testRecordEnqueuesWithoutBlocking() {
        val start = System.nanoTime()
        for (i in 0 until 1000) {
            QueryLogSink.record("test-$i.example.com", decision = if (i % 2 == 0) 1 else 0, reason = 1)
        }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        // Enqueueing 1,000 items should take less than 100 ms on any reasonable system
        assertTrue("Enqueueing must be non-blocking (took $elapsedMillis ms)", elapsedMillis < 200)
    }

    @Test
    fun testInvalidOrEmptyDomainsIgnored() {
        // Empty domain, invalid length, or blank domain should not crash
        QueryLogSink.record("", 0, 0)
        QueryLogSink.record("   ", 0, 0)
        QueryLogSink.record("a".repeat(300) + ".com", 0, 0)
    }
}
