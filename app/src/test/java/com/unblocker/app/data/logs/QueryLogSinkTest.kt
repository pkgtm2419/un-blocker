package com.unblocker.app.data.logs

import com.unblocker.app.services.VpnDiagnostics
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueryLogSinkTest {

    private class FakeDomainLogDao(
        private val throwOnUpsert: Boolean = false,
        private val throwOnInsertEvents: Boolean = false
    ) : DomainLogDao {
        val upsertedStats = mutableListOf<String>()
        val insertedEvents = mutableListOf<DomainEventEntity>()

        override suspend fun upsertStat(
            domain: String,
            callDelta: Long,
            blockedDelta: Long,
            allowedDelta: Long,
            ts: Long,
            lastDecision: Int,
            lastReason: Int
        ) {
            if (throwOnUpsert) throw RuntimeException("Simulated SQLite write error")
            upsertedStats.add(domain)
        }

        override suspend fun insertEvents(events: List<DomainEventEntity>) {
            if (throwOnInsertEvents) throw RuntimeException("Simulated SQLite write error")
            insertedEvents.addAll(events)
        }

        override fun getDomainStatsFlow(filter: Int, query: String?, sortByCalls: Int, limit: Int): Flow<List<DomainStatEntity>> = emptyFlow()
        override suspend fun getEventsForDomain(domain: String, limit: Int): List<DomainEventEntity> = emptyList()
        override fun getTotalDomainsCountFlow(): Flow<Long> = emptyFlow()
        override fun getTotalCallsCountFlow(): Flow<Long> = emptyFlow()
        override fun getTotalBlockedCountFlow(): Flow<Long> = emptyFlow()
        override suspend fun pruneEventsOlderThan(cutoffTimestamp: Long) {}
        override suspend fun pruneOldestEventsBeyondCap(globalCap: Int) {}
        override suspend fun clearEvents() {}
        override suspend fun clearStats() {}
    }

    @Before
    fun setup() {
        QueryLogSink.clearQueue()
        QueryLogSink.setLoggingPaused(false)
        QueryLogSink.setHistoryMode(QueryLogSink.HistoryMode.AGGREGATE_ONLY)
        VpnDiagnostics.resetForTesting()
    }

    @Test
    fun testDefaultHistoryModeIsAggregateOnly() {
        assertEquals(QueryLogSink.HistoryMode.AGGREGATE_ONLY, QueryLogSink.getHistoryMode())
    }

    @Test
    fun testAggregateOnlyModeDoesNotStoreRawEvents() = runBlocking {
        val fakeDao = FakeDomainLogDao()
        QueryLogSink.init(fakeDao)
        QueryLogSink.setHistoryMode(QueryLogSink.HistoryMode.AGGREGATE_ONLY)

        val items = listOf(
            QueryLogSink.LogItem("example.com", decision = 0, reason = 0, ts = 1000L),
            QueryLogSink.LogItem("blocked-tracker.com", decision = 1, reason = 1, ts = 2000L)
        )

        QueryLogSink.flushBatch(items)

        // Aggregated stats are updated
        assertEquals(2, fakeDao.upsertedStats.size)
        assertTrue(fakeDao.upsertedStats.contains("example.com"))
        assertTrue(fakeDao.upsertedStats.contains("blocked-tracker.com"))

        // Raw events are NOT stored
        assertTrue("Under AGGREGATE_ONLY, no raw events should be inserted", fakeDao.insertedEvents.isEmpty())
    }

    @Test
    fun testOptInModeStoresBothAggregatesAndRawEvents() = runBlocking {
        val fakeDao = FakeDomainLogDao()
        QueryLogSink.init(fakeDao)
        QueryLogSink.setHistoryMode(QueryLogSink.HistoryMode.RAW_HISTORY_OPT_IN)

        val items = listOf(
            QueryLogSink.LogItem("opt-in.com", decision = 0, reason = 0, ts = 3000L)
        )

        QueryLogSink.flushBatch(items)

        assertEquals(1, fakeDao.upsertedStats.size)
        assertEquals(1, fakeDao.insertedEvents.size)
        assertEquals("opt-in.com", fakeDao.insertedEvents.first().domain)
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
        assertTrue("Enqueueing must be non-blocking (took $elapsedMillis ms)", elapsedMillis < 200)
    }

    @Test
    fun testInvalidOrEmptyDomainsIgnored() {
        QueryLogSink.record("", 0, 0)
        QueryLogSink.record("   ", 0, 0)
        QueryLogSink.record("a".repeat(300) + ".com", 0, 0)
    }
}
