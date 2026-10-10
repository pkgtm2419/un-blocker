package com.unblocker.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.logs.QueryLogSink
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import com.unblocker.data.repository.FilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * - History clearing and logging pause.
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    private val domainLogDao: DomainLogDao,
    private val filterRepository: FilterRepository
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

    private val _isLoggingPaused = MutableStateFlow(QueryLogSink.isLoggingPaused())
    val isLoggingPaused: StateFlow<Boolean> = _isLoggingPaused.asStateFlow()

    val totalDomains: StateFlow<Long> = domainLogDao.getTotalDomainsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalCalls: StateFlow<Long> = domainLogDao.getTotalCallsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalBlocked: StateFlow<Long> = domainLogDao.getTotalBlockedCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    @OptIn(ExperimentalCoroutinesApi::class)
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
        )
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
        QueryLogSink.setLoggingPaused(newState)
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            QueryLogSink.clearHistory()
            _expandedDomain.value = null
            _domainEvents.value = emptyList()
        }
    }

    fun allowDomain(domain: String) {
        viewModelScope.launch {
            filterRepository.whitelistDomain(domain)
        }
    }

    fun blockDomain(domain: String) {
        viewModelScope.launch {
            filterRepository.blacklistDomain(domain)
        }
    }
}
