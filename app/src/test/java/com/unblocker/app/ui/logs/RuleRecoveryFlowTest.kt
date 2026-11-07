package com.unblocker.app.ui.logs

import com.unblocker.app.data.preferences.FilteringPreferenceStore
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleRecoveryFlowTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeUserRuleGateway : UserRuleGateway {
        val rules = mutableMapOf<String, UserFeedback>()
        var shouldFail = false

        override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult {
            if (shouldFail) return RuleUpdateResult.Failed("Disk write error")
            rules[domain] = feedback
            return RuleUpdateResult.Applied
        }

        override suspend fun setWholeSite(domain: String, feedback: UserFeedback): RuleUpdateResult {
            if (shouldFail) return RuleUpdateResult.Failed("Disk write error")
            rules["subtree:$domain"] = feedback
            return RuleUpdateResult.Applied
        }

        override suspend fun protectDomain(domain: String): RuleUpdateResult {
            if (shouldFail) return RuleUpdateResult.Failed("Disk write error")
            rules["protected:$domain"] = UserFeedback.ALLOW
            return RuleUpdateResult.Applied
        }

        override suspend fun reset(domain: String): RuleUpdateResult {
            if (shouldFail) return RuleUpdateResult.Failed("Disk write error")
            rules.remove(domain)
            return RuleUpdateResult.Applied
        }
    }

    private class FakeFilteringPreferenceStore : FilteringPreferenceStore {
        private val _isQueryLoggingPaused = MutableStateFlow(false)
        override val isQueryLoggingPaused: StateFlow<Boolean> = _isQueryLoggingPaused.asStateFlow()

        override fun setQueryLoggingPaused(paused: Boolean) {
            _isQueryLoggingPaused.value = paused
        }
    }

    private class DummyDomainLogDao : DomainLogDao {
        override suspend fun upsertStat(domain: String, callDelta: Long, blockedDelta: Long, allowedDelta: Long, ts: Long, lastDecision: Int, lastReason: Int) {}
        override suspend fun insertEvents(events: List<DomainEventEntity>) {}
        override fun getDomainStatsFlow(filter: Int, query: String?, sortByCalls: Int, limit: Int): Flow<List<DomainStatEntity>> = emptyFlow()
        override fun getDomainStatsByCallsFlow(filter: Int, query: String?, limit: Int): Flow<List<DomainStatEntity>> = emptyFlow()
        override fun getDomainStatsByRecentFlow(filter: Int, query: String?, limit: Int): Flow<List<DomainStatEntity>> = emptyFlow()
        override fun getAggregateStatsFlow(): Flow<com.unblocker.data.db.dao.DomainLogAggregate> = emptyFlow()
        override suspend fun getEventsForDomain(domain: String, limit: Int): List<DomainEventEntity> = emptyList()
        override suspend fun getAllStatsForExport(): List<DomainStatEntity> = emptyList()
        override fun getTotalDomainsCountFlow(): Flow<Long> = emptyFlow()
        override fun getTotalCallsCountFlow(): Flow<Long> = emptyFlow()
        override fun getTotalBlockedCountFlow(): Flow<Long> = emptyFlow()
        override fun getFirstObservedTimeFlow(): Flow<Long?> = emptyFlow()
        override fun getLastObservedTimeFlow(): Flow<Long?> = emptyFlow()
        override suspend fun pruneEventsOlderThan(cutoffTimestamp: Long) {}
        override suspend fun pruneOldestEventsBeyondCap(globalCap: Int) {}
        override suspend fun pruneStatsOlderThan(cutoffTimestamp: Long) {}
        override suspend fun pruneOldestStatsBeyondCap(globalCap: Int) {}
        override suspend fun clearEvents() {}
        override suspend fun clearStats() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAllowDomainAppliesFeedbackAndEmitsMessage() = runTest(testDispatcher) {
        val gateway = FakeUserRuleGateway()
        val dao = DummyDomainLogDao()
        val prefs = FakeFilteringPreferenceStore()
        val viewModel = LogViewModel(dao, gateway, prefs)

        viewModel.allowDomain("example.com")
        advanceUntilIdle()

        assertEquals(UserFeedback.ALLOW, gateway.rules["example.com"])
        assertEquals("Allowed: example.com", viewModel.userMessage.value)
    }

    @Test
    fun testBlockDomainAppliesFeedbackAndEmitsMessage() = runTest(testDispatcher) {
        val gateway = FakeUserRuleGateway()
        val dao = DummyDomainLogDao()
        val prefs = FakeFilteringPreferenceStore()
        val viewModel = LogViewModel(dao, gateway, prefs)

        viewModel.blockDomain("tracker.adnetwork.com")
        advanceUntilIdle()

        assertEquals(UserFeedback.BLOCK, gateway.rules["tracker.adnetwork.com"])
        assertEquals("Blocked: tracker.adnetwork.com", viewModel.userMessage.value)
    }

    @Test
    fun testFailedRuleUpdateSurfacesErrorMessage() = runTest(testDispatcher) {
        val gateway = FakeUserRuleGateway().apply { shouldFail = true }
        val dao = DummyDomainLogDao()
        val prefs = FakeFilteringPreferenceStore()
        val viewModel = LogViewModel(dao, gateway, prefs)

        viewModel.allowDomain("example.com")
        advanceUntilIdle()

        assertEquals("Failed: Disk write error", viewModel.userMessage.value)
    }
}
