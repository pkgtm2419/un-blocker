package com.unblocker.app.ui.logs

import com.unblocker.app.data.logs.QueryLogSink
import com.unblocker.app.data.preferences.FilteringPreferenceStore
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.DomainLogAggregate
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying multi-select batch rule actions and retention pruning in [LogViewModel] (WP-24 / F33).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelBatchTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakePrefs : FilteringPreferenceStore {
        private val paused = MutableStateFlow(false)
        override val isQueryLoggingPaused: StateFlow<Boolean> = paused

        override fun setQueryLoggingPaused(paused: Boolean) {
            this.paused.value = paused
        }
    }

    private class RecordingGateway : UserRuleGateway {
        val recordedCalls = mutableListOf<Pair<String, UserFeedback>>()

        override suspend fun set(
            domain: String,
            feedback: UserFeedback,
        ): RuleUpdateResult {
            recordedCalls.add(domain to feedback)
            return RuleUpdateResult.Applied
        }

        override suspend fun setWholeSite(
            domain: String,
            feedback: UserFeedback,
        ): RuleUpdateResult = RuleUpdateResult.Applied

        override suspend fun protectDomain(domain: String): RuleUpdateResult = RuleUpdateResult.Applied

        override suspend fun reset(domain: String): RuleUpdateResult = RuleUpdateResult.Applied
    }

    private class StubDao : DomainLogDao {
        var prunedEvents = false
        var prunedStats = false

        override suspend fun upsertStat(
            domain: String,
            callDelta: Long,
            blockedDelta: Long,
            allowedDelta: Long,
            ts: Long,
            lastDecision: Int,
            lastReason: Int,
            failDelta: Long,
            lastOutcome: Int,
            lastRule: String,
            lastSource: String,
            lastNetworkType: String,
        ) = Unit

        override suspend fun insertEvents(events: List<DomainEventEntity>) = Unit

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

        override fun getAggregateStatsFlow(): Flow<DomainLogAggregate> = emptyFlow()

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

        override suspend fun pruneEventsOlderThan(cutoffTimestamp: Long) {
            prunedEvents = true
        }

        override suspend fun pruneOldestEventsBeyondCap(globalCap: Int) = Unit

        override suspend fun pruneStatsOlderThan(cutoffTimestamp: Long) {
            prunedStats = true
        }

        override suspend fun pruneOldestStatsBeyondCap(globalCap: Int) = Unit

        override suspend fun clearEvents() = Unit

        override suspend fun clearStats() = Unit

        override suspend fun clearAll() = Unit
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        QueryLogSink.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun multiSelectAndBatchRuleApplicationClearsSelection() =
        runBlocking {
            val dao = StubDao()
            val gateway = RecordingGateway()
            val vm = LogViewModel(dao, gateway, FakePrefs())

            vm.toggleSelectDomain("ads.example.com")
            vm.toggleSelectDomain("tracker.example.com")
            assertEquals(setOf("ads.example.com", "tracker.example.com"), vm.selectedDomains.value)

            vm.toggleSelectDomain("ads.example.com")
            assertEquals(setOf("tracker.example.com"), vm.selectedDomains.value)

            vm.selectVisibleDomains(listOf("b.example.org", "a.example.org"))
            vm.batchBlockSelected()
            delay(20)

            assertEquals(
                listOf("a.example.org" to UserFeedback.BLOCK, "b.example.org" to UserFeedback.BLOCK),
                gateway.recordedCalls,
            )
            assertTrue(vm.selectedDomains.value.isEmpty())
            assertTrue(vm.userMessage.value?.contains("2") == true)
        }

    @Test
    fun pruneExpiredLogsDispatchesRetentionPruning() =
        runBlocking {
            val dao = StubDao()
            QueryLogSink.init(dao)
            val vm = LogViewModel(dao, RecordingGateway(), FakePrefs())

            vm.pruneExpiredLogs()
            delay(20)

            assertTrue(dao.prunedEvents)
            assertTrue(dao.prunedStats)
        }
}
