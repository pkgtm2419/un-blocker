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

/**
 * Regression coverage for query log sink.
 */
class QueryLogSinkTest {
    private class FakeDomainLogDao(
        private val throwOnUpsert: Boolean = false,
        private val throwOnInsertEvents: Boolean = false,
    ) : DomainLogDao {
        val upsertedStats = mutableListOf<String>()
        val insertedEvents = mutableListOf<DomainEventEntity>()
        var cleared = false

        override suspend fun upsertStat(
            domain: String,
            callDelta: Long,
            blockedDelta: Long,
            allowedDelta: Long,
            ts: Long,
            lastDecision: Int,
            lastReason: Int,
        ) {
            if (throwOnUpsert) throw RuntimeException("Simulated SQLite write error")
            upsertedStats.add(domain)
        }

        override suspend fun insertEvents(events: List<DomainEventEntity>) {
            if (throwOnInsertEvents) throw RuntimeException("Simulated SQLite write error")
            insertedEvents.addAll(events)
        }

        override fun getDomainStatsFlow(
            filter: Int,
            query: String?,
            sortByCalls: Int,
            limit: Int,
        ): Flow<List<DomainStatEntity>> = emptyFlow()

        override fun getDomainStatsByCallsFlow(
            filter: Int,
            query: String?,
            limit: Int,
        ): Flow<List<DomainStatEntity>> = emptyFlow()

        override fun getDomainStatsByRecentFlow(
            filter: Int,
            query: String?,
            limit: Int,
        ): Flow<List<DomainStatEntity>> = emptyFlow()

        override fun getAggregateStatsFlow(): Flow<com.unblocker.data.db.dao.DomainLogAggregate> = emptyFlow()

        override suspend fun getEventsForDomain(
            domain: String,
            limit: Int,
        ): List<DomainEventEntity> = emptyList()

        override suspend fun getAllStatsForExport(): List<DomainStatEntity> = emptyList()

        override fun getTotalDomainsCountFlow(): Flow<Long> = emptyFlow()

        override fun getTotalCallsCountFlow(): Flow<Long> = emptyFlow()

        override fun getTotalBlockedCountFlow(): Flow<Long> = emptyFlow()

        override fun getFirstObservedTimeFlow(): Flow<Long?> = emptyFlow()

        override fun getLastObservedTimeFlow(): Flow<Long?> = emptyFlow()

        var prunedEventsCutoff: Long? = null
        var prunedEventsCap: Int? = null
        var prunedStatsCutoff: Long? = null
        var prunedStatsCap: Int? = null

        override suspend fun pruneEventsOlderThan(cutoffTimestamp: Long) {
            prunedEventsCutoff = cutoffTimestamp
        }

        override suspend fun pruneOldestEventsBeyondCap(globalCap: Int) {
            prunedEventsCap = globalCap
        }

        override suspend fun pruneStatsOlderThan(cutoffTimestamp: Long) {
            prunedStatsCutoff = cutoffTimestamp
        }

        override suspend fun pruneOldestStatsBeyondCap(globalCap: Int) {
            prunedStatsCap = globalCap
        }

        override suspend fun clearEvents() {
            insertedEvents.clear()
        }

        override suspend fun clearStats() {
            upsertedStats.clear()
        }

        override suspend fun clearAll() {
            cleared = true
            clearEvents()
            clearStats()
        }
    }

    /**
     * Resets the fixture state before each test to keep assertions independent.
     */
    @Before
    fun setup() {
        QueryLogSink.resetForTesting()
        VpnDiagnostics.resetForTesting()
    }

    /**
     * Verifies that test default history mode is aggregate only.
     */
    @Test
    fun testDefaultHistoryModeIsAggregateOnly() {
        assertEquals(QueryLogSink.HistoryMode.AGGREGATE_ONLY, QueryLogSink.getHistoryMode())
    }

    /**
     * Verifies that test aggregate only mode does not store raw events.
     */
    @Test
    fun testAggregateOnlyModeDoesNotStoreRawEvents() =
        runBlocking {
            val fakeDao = FakeDomainLogDao()
            QueryLogSink.init(fakeDao)
            QueryLogSink.setHistoryMode(QueryLogSink.HistoryMode.AGGREGATE_ONLY)

            val items =
                listOf(
                    QueryLogSink.LogItem("example.com", decision = 0, reason = 0, ts = 1000L, generation = 1L),
                    QueryLogSink.LogItem("blocked-tracker.com", decision = 1, reason = 1, ts = 2000L, generation = 1L),
                )

            QueryLogSink.flushBatch(items)

            // Aggregated stats are updated
            assertEquals(2, fakeDao.upsertedStats.size)
            assertTrue(fakeDao.upsertedStats.contains("example.com"))
            assertTrue(fakeDao.upsertedStats.contains("blocked-tracker.com"))

            // Raw events are NOT stored
            assertTrue("Under AGGREGATE_ONLY, no raw events should be inserted", fakeDao.insertedEvents.isEmpty())
        }

    /**
     * Verifies that test opt in mode stores both aggregates and raw events.
     */
    @Test
    fun testOptInModeStoresBothAggregatesAndRawEvents() =
        runBlocking {
            val fakeDao = FakeDomainLogDao()
            QueryLogSink.init(fakeDao)
            QueryLogSink.setHistoryMode(QueryLogSink.HistoryMode.RAW_HISTORY_OPT_IN)

            val items =
                listOf(
                    QueryLogSink.LogItem("opt-in.com", decision = 0, reason = 0, ts = 3000L, generation = 1L),
                )

            QueryLogSink.flushBatch(items)

            assertEquals(1, fakeDao.upsertedStats.size)
            assertEquals(1, fakeDao.insertedEvents.size)
            assertEquals("opt-in.com", fakeDao.insertedEvents.first().domain)
        }

    /**
     * Verifies that test pause and resume logging.
     */
    @Test
    fun testPauseAndResumeLogging() {
        assertFalse(QueryLogSink.isLoggingPaused())

        QueryLogSink.setLoggingPaused(true)
        assertTrue(QueryLogSink.isLoggingPaused())

        QueryLogSink.setLoggingPaused(false)
        assertFalse(QueryLogSink.isLoggingPaused())
    }

    /**
     * Verifies that test record enqueues without blocking.
     */
    @Test
    fun testRecordEnqueuesWithoutBlocking() {
        val start = System.nanoTime()
        for (i in 0 until 1000) {
            QueryLogSink.record("test-$i.example.com", decision = if (i % 2 == 0) 1 else 0, reason = 1)
        }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000
        assertTrue("Enqueueing must be non-blocking (took $elapsedMillis ms)", elapsedMillis < 200)
    }

    /**
     * Verifies that test invalid or empty domains ignored.
     */
    @Test
    fun testInvalidOrEmptyDomainsIgnored() {
        QueryLogSink.record("", 0, 0)
        QueryLogSink.record("   ", 0, 0)
        QueryLogSink.record("a".repeat(300) + ".com", 0, 0)
    }

    /**
     * Verifies that test clear history and barrier prevents pre clear items from flushing.
     */
    @Test
    fun testClearHistoryAndBarrierPreventsPreClearItemsFromFlushing() =
        runBlocking {
            val fakeDao = FakeDomainLogDao()
            QueryLogSink.init(fakeDao)

            // Queue pre-clear item
            QueryLogSink.record("pre-clear-tracker.com", 1, 1)

            // Invoke clearHistoryAndBarrier
            val clearResult = QueryLogSink.clearHistoryAndBarrier()
            assertTrue(clearResult is QueryLogSink.ClearResult.Success)
            assertTrue(fakeDao.cleared)

            // Simulate an in-flight batch that had pre-clear generation items
            val preClearBatch =
                listOf(
                    QueryLogSink.LogItem("pre-clear-tracker.com", 1, 1, 1000L, generation = 1L),
                )
            QueryLogSink.flushBatch(preClearBatch)

            // Pre-clear item must NOT be written to the database
            assertFalse(fakeDao.upsertedStats.contains("pre-clear-tracker.com"))
        }

    /**
     * Verifies that pruneOldLogs triggers deletion of expired events and stats with configured caps.
     */
    @Test
    fun testPruneOldLogsAppliesRetentionLimitsAndCaps() =
        runBlocking {
            val fakeDao = FakeDomainLogDao()
            QueryLogSink.init(fakeDao)

            val before = System.currentTimeMillis()
            QueryLogSink.pruneOldLogs()
            val after = System.currentTimeMillis()

            // 24 hour event retention
            val expectedOneDay = 24L * 60 * 60 * 1000
            assertTrue(
                fakeDao.prunedEventsCutoff != null &&
                    fakeDao.prunedEventsCutoff!! in (before - expectedOneDay)..(after - expectedOneDay),
            )
            assertEquals(1_000, fakeDao.prunedEventsCap)

            // 30 day aggregate retention
            val expectedThirtyDays = 30L * 24 * 60 * 60 * 1000
            assertTrue(
                fakeDao.prunedStatsCutoff != null &&
                    fakeDao.prunedStatsCutoff!! in (before - expectedThirtyDays)..(after - expectedThirtyDays),
            )
            assertEquals(10_000, fakeDao.prunedStatsCap)
        }
}
