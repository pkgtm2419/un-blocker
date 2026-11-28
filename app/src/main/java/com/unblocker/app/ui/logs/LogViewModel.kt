package com.unblocker.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.logs.QueryLogSink
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

/**
 * Domain aggregate filter passed to the DAO query.
 *
 * @property value DAO filter code: zero for all, one for blocked, two for allowed.
 */
enum class LogFilter(
    val value: Int,
) {
    ALL(0),
    BLOCKED(1),
    ALLOWED(2),
    FAILED(3),
}

/**
 * Domain aggregate ordering passed to the DAO query.
 *
 * @property value DAO sort code: zero for recent activity, one for greatest call count.
 */
enum class LogSort(
    val value: Int,
) {
    RECENT(0),
    MOST_CALLS(1),
}

/**
 * Owns local log queries, history controls, exports, and user rule feedback for the Logs tab.
 *
 * @param domainLogDao Source of readable domain aggregates and optional recent events.
 * @param userRuleGateway Destination for user rule and protected-site changes.
 * @param preferences Persisted query logging pause preference boundary.
 */
@HiltViewModel
class LogViewModel
    @Inject
    constructor(
        private val domainLogDao: DomainLogDao,
        private val userRuleGateway: UserRuleGateway,
        private val preferences: com.unblocker.app.data.preferences.FilteringPreferenceStore,
    ) : ViewModel() {
        private val _filter = MutableStateFlow(LogFilter.ALL)

        /**
         * Current domain aggregate decision filter.
         */
        val filter: StateFlow<LogFilter> = _filter.asStateFlow()

        private val _sort = MutableStateFlow(LogSort.RECENT)

        /**
         * Current domain aggregate ordering.
         */
        val sort: StateFlow<LogSort> = _sort.asStateFlow()

        private val _searchQuery = MutableStateFlow("")

        /**
         * Current untrimmed domain search text.
         */
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _expandedDomain = MutableStateFlow<String?>(null)

        /**
         * Domain whose recent-event section is expanded, or null when none is expanded.
         */
        val expandedDomain: StateFlow<String?> = _expandedDomain.asStateFlow()

        private val _domainEvents = MutableStateFlow<List<DomainEventEntity>>(emptyList())

        /**
         * Most recently loaded event rows for an expanded domain; aggregate-only logging may yield none.
         */
        val domainEvents: StateFlow<List<DomainEventEntity>> = _domainEvents.asStateFlow()

        private val _isLoggingPaused = MutableStateFlow(preferences.isQueryLoggingPaused.value)

        /**
         * Pause state exposed by this ViewModel and synchronized to the sink when toggled here.
         */
        val isLoggingPaused: StateFlow<Boolean> = _isLoggingPaused.asStateFlow()

        private val aggregateFlow =
            domainLogDao
                .getAggregateStatsFlow()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    com.unblocker.data.db.dao
                        .DomainLogAggregate(0L, 0L, 0L, null, null),
                )

        /**
         * Total distinct domains in local aggregate storage.
         */
        val totalDomains: StateFlow<Long> =
            aggregateFlow
                .map { it.totalDomains }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        /**
         * Total recorded query count across local aggregates.
         */
        val totalCalls: StateFlow<Long> =
            aggregateFlow
                .map { it.totalCalls }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        /**
         * Total blocked-query count across local aggregates.
         */
        val totalBlocked: StateFlow<Long> =
            aggregateFlow
                .map { it.totalBlocked }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        /**
         * Local query totals and observation timestamps without inferred advertising or bandwidth metrics.
         */
        val protectionSummary: StateFlow<com.unblocker.app.ui.model.LocalProtectionSummary> =
            aggregateFlow
                .map { aggregate ->
                    com.unblocker.app.ui.model.LocalProtectionSummary(
                        dnsQueries = aggregate.totalCalls,
                        blockedDnsQueries = aggregate.totalBlocked,
                        firstObservedMs = aggregate.firstObserved,
                        lastObservedMs = aggregate.lastObserved,
                    )
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    com.unblocker.app.ui.model
                        .LocalProtectionSummary(0L, 0L, null, null),
                )

        /**
         * Domain aggregates matching the selected filter, ordering, and trimmed search query.
         */
        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val domainStats: StateFlow<List<DomainStatEntity>> =
            combine(
                _filter,
                _sort,
                _searchQuery,
            ) { filter, sort, query ->
                Triple(filter, sort, query.trim().ifEmpty { null })
            }.flatMapLatest { (filter, sort, query) ->
                domainLogDao
                    .getDomainStatsFlow(
                        filter = filter.value,
                        query = query,
                        sortByCalls = sort.value,
                    ).debounce(300L)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        /**
         * Selects which aggregate decisions are shown.
         *
         * @param newFilter Decision filter to apply.
         */
        fun setFilter(newFilter: LogFilter) {
            _filter.value = newFilter
        }

        /**
         * Selects the domain aggregate ordering.
         *
         * @param newSort Ordering to apply.
         */
        fun setSort(newSort: LogSort) {
            _sort.value = newSort
        }

        /**
         * Updates text used by the DAO domain search.
         *
         * @param query Search text; blank text is passed to the DAO as no query.
         */
        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        /**
         * Collapses the current domain or expands another and loads its recent events asynchronously.
         *
         * @param domain Readable domain whose event section should toggle.
         */
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

        /**
         * Toggles new log capture and persists the pause flag while retaining queued and stored history.
         */
        fun toggleLoggingPaused() {
            val newState = !_isLoggingPaused.value
            _isLoggingPaused.value = newState
            preferences.setQueryLoggingPaused(newState)
            QueryLogSink.setLoggingPaused(newState)
        }

        /**
         * Launches history deletion through the sink generation barrier, then clears expanded-event state.
         */
        fun clearAllLogs() {
            viewModelScope.launch {
                QueryLogSink.clearHistoryAndBarrier()
                _expandedDomain.value = null
                _domainEvents.value = emptyList()
            }
        }

        private val _userMessage = MutableStateFlow<String?>(null)

        /**
         * Pending user-facing result message, or null after acknowledgement.
         */
        val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

        /**
         * Acknowledges and clears the pending result message.
         */
        fun clearUserMessage() {
            _userMessage.value = null
        }

        /**
         * Asynchronously saves an exact-domain allow override and publishes the result.
         *
         * @param domain Host to normalize and allow.
         */
        fun allowDomain(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.set(domain, UserFeedback.ALLOW)) {
                    is RuleUpdateResult.Applied -> _userMessage.value = "Allowed: $domain"
                    is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
                }
            }
        }

        /**
         * Asynchronously requests an admitted exact-domain block override and publishes the result.
         *
         * @param domain Host to normalize and block.
         */
        fun blockDomain(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.set(domain, UserFeedback.BLOCK)) {
                    is RuleUpdateResult.Applied -> _userMessage.value = "Blocked: $domain"
                    is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
                }
            }
        }

        /**
         * Asynchronously removes the exact-domain override and publishes the result.
         *
         * @param domain Host whose exact override should be removed.
         */
        fun resetDomain(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.set(domain, UserFeedback.NONE)) {
                    is RuleUpdateResult.Applied -> _userMessage.value = "Reset rule: $domain"
                    is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
                }
            }
        }

        /**
         * Asynchronously saves an allow override for the registrable site and its descendants.
         *
         * @param domain Host used to identify the site.
         */
        fun allowWholeSite(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.setWholeSite(domain, UserFeedback.ALLOW)) {
                    is RuleUpdateResult.Applied -> _userMessage.value = "Allowed whole site: $domain"
                    is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
                }
            }
        }

        /**
         * Asynchronously adds a readable site to the separate My domains protection list.
         *
         * @param domain Host used to identify the site.
         */
        fun protectDomain(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.protectDomain(domain)) {
                    is RuleUpdateResult.Applied -> _userMessage.value = "Protected: $domain"
                    is RuleUpdateResult.Failed -> _userMessage.value = "Failed: ${result.reason}"
                }
            }
        }

        /**
         * Delegates to exact-domain override removal.
         *
         * @param domain Host whose exact override should be removed.
         */
        fun resetRule(domain: String) {
            resetDomain(domain)
        }

        /**
         * Exports all local domain aggregates and publishes the number of exported data rows.
         *
         * @param outputStream Destination stream, which the exporter flushes and closes.
         * @return Number of aggregate rows exported, excluding the CSV header.
         * @throws java.io.IOException If the exporter cannot write, flush, or close the stream.
         */
        suspend fun exportLogsToCsv(outputStream: java.io.OutputStream): Int {
            val stats = domainLogDao.getAllStatsForExport()
            val count =
                com.unblocker.app.data.export.DomainExporter
                    .exportToCsv(stats, outputStream)
            _userMessage.value = "Exported $count domains to CSV"
            return count
        }

        /**
         * Exports all local domain aggregates as schema-versioned UTF-8 JSON (WP-22 / F31).
         */
        suspend fun exportLogsToJson(outputStream: java.io.OutputStream): Int {
            val stats = domainLogDao.getAllStatsForExport()
            val count =
                com.unblocker.app.data.export.DomainExporter
                    .exportToJson(stats, outputStream)
            _userMessage.value = "Exported $count domains to JSON"
            return count
        }
    }
