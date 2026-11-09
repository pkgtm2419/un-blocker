package com.unblocker.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.logs.QueryLogSink
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter(val value: Int) {
    ALL(0),
    BLOCKED(1),
    ALLOWED(2)
}

enum class LogSort(val value: Int) {
    RECENT(0),
    MOST_CALLS(1)
}

/**
 * ViewModel for the Logs tab.
 *
 * Implements WP-3 & WP-4 requirements:
 * - Shows each domain once with total call counts and last seen.
 * - Supports expanding domain for timestamp history.
 * - Filter chips (All / Blocked / Allowed) and sort options (Recent / Most calls).
 * - Total domain, call, and blocked statistics.
 * - History clearing and logging pause with barrier guarantees.
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    private val domainLogDao: DomainLogDao,
    private val userRuleGateway: UserRuleGateway,
    private val preferences: com.unblocker.app.data.preferences.FilteringPreferenceStore
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(LogSort.RECENT)
    val sort: StateFlow<LogSort> = _sort.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _expandedDomain = MutableStateFlow<String?>(null)
    val expandedDomain: StateFlow<String?> = _expandedDomain.asStateFlow()

    private val _domainEvents = MutableStateFlow<List<DomainEventEntity>>(emptyList())
    val domainEvents: StateFlow<List<DomainEventEntity>> = _domainEvents.asStateFlow()

    private val _isLoggingPaused = MutableStateFlow(preferences.isQueryLoggingPaused.value)
    val isLoggingPaused: StateFlow<Boolean> = _isLoggingPaused.asStateFlow()

    private val aggregateFlow = domainLogDao.getAggregateStatsFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.unblocker.data.db.dao.DomainLogAggregate(0L, 0L, 0L, null, null)
        )

    val totalDomains: StateFlow<Long> = aggregateFlow.map { it.totalDomains }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalCalls: StateFlow<Long> = aggregateFlow.map { it.totalCalls }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalBlocked: StateFlow<Long> = aggregateFlow.map { it.totalBlocked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val protectionSummary: StateFlow<com.unblocker.app.ui.model.LocalProtectionSummary> = aggregateFlow.map { agg ->
        com.unblocker.app.ui.model.LocalProtectionSummary(
            dnsQueries = agg.totalCalls,
            blockedDnsQueries = agg.totalBlocked,
            firstObservedMs = agg.firstObserved,
            lastObservedMs = agg.lastObserved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.unblocker.app.ui.model.LocalProtectionSummary(0L, 0L, null, null))

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val domainStats: StateFlow<List<DomainStatEntity>> = combine(
        _filter,
        _sort,
        _searchQuery
    ) { filter, sort, query ->
        Triple(filter, sort, query.trim().ifEmpty { null })
    }.flatMapLatest { (filter, sort, query) ->
        domainLogDao.getDomainStatsFlow(
            filter = filter.value,
            query = query,
            sortByCalls = sort.value
        ).debounce(300L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(newFilter: LogFilter) {
        _filter.value = newFilter
    }

    fun setSort(newSort: LogSort) {
        _sort.value = newSort
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleExpandDomain(domain: String) {
        if (_expandedDomain.value == domain) {
            _expandedDomain.value = null
            _domainEvents.value = emptyList()
        } else {
            _expandedDomain.value = domain
            viewModelScope.launch {
                _domainEvents.value = domainLogDao.getEventsForDomain(domain)
            }
        }
    }

    fun toggleLoggingPaused() {
        val newState = !_isLoggingPaused.value
        _isLoggingPaused.value = newState
        preferences.setQueryLoggingPaused(newState)
        QueryLogSink.setLoggingPaused(newState)
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            QueryLogSink.clearHistoryAndBarrier()
            _expandedDomain.value = null
            _domainEvents.value = emptyList()
        }
    }

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun allowDomain(domain: String) {
        viewModelScope.launch {
            when (val result = userRuleGateway.set(domain, UserFeedback.ALLOW)) {
                is RuleUpdateResult.Applied -> _userMessage.value = "Allowed: $domain"
                is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
            }
        }
    }

    fun blockDomain(domain: String) {
        viewModelScope.launch {
            when (val result = userRuleGateway.set(domain, UserFeedback.BLOCK)) {
                is RuleUpdateResult.Applied -> _userMessage.value = "Blocked: $domain"
                is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
            }
        }
    }

    fun resetDomain(domain: String) {
        viewModelScope.launch {
            when (val result = userRuleGateway.set(domain, UserFeedback.NONE)) {
                is RuleUpdateResult.Applied -> _userMessage.value = "Reset rule: $domain"
                is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
            }
        }
    }

    fun allowWholeSite(domain: String) {
        viewModelScope.launch {
            when (val result = userRuleGateway.setWholeSite(domain, UserFeedback.ALLOW)) {
                is RuleUpdateResult.Applied -> _userMessage.value = "Allowed whole site: $domain"
                is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
            }
        }
    }

    fun protectDomain(domain: String) {
        viewModelScope.launch {
            when (val result = userRuleGateway.protectDomain(domain)) {
                is RuleUpdateResult.Applied -> _userMessage.value = "Protected: $domain"
                is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
            }
        }
    }

    fun resetRule(domain: String) {
        resetDomain(domain)
    }

    suspend fun exportLogsToCsv(outputStream: java.io.OutputStream): Int {
        val stats = domainLogDao.getAllStatsForExport()
        val count = com.unblocker.app.data.export.DomainExporter.exportToCsv(stats, outputStream)
        _userMessage.value = "Exported $count domains to CSV"
        return count
    }
}
