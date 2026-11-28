package com.unblocker.app.ui.exceptions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.model.FilterResult
import com.unblocker.app.data.preferences.MyDomainsStore
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.ContentFilterEngine
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.entity.UserRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * Active tab selection in the Exceptions screen.
 */
enum class ExceptionTab {
    ALLOW,
    BLOCK,
}

/**
 * Snapshot state rendered by [ExceptionsScreen].
 *
 * @property allowRules Readable allow overrides matching [searchQuery].
 * @property blockRules Readable block overrides matching [searchQuery].
 * @property totalAllowCount Unfiltered allow rule count.
 * @property totalBlockCount Unfiltered block rule count.
 * @property protectedDomains Read-only My domains protected hosts matching [searchQuery].
 * @property unlistedHmacCount Count of HMAC entries not mirrored in `user_rule` (e.g. pre-v3.0 rules).
 * @property selectedTab Currently selected tab.
 * @property searchQuery Current domain search filter text.
 * @property testResult Most recent domain rule-tester evaluation, or null if not yet run.
 */
data class ExceptionsUiState(
    val allowRules: List<UserRuleEntity> = emptyList(),
    val blockRules: List<UserRuleEntity> = emptyList(),
    val totalAllowCount: Int = 0,
    val totalBlockCount: Int = 0,
    val protectedDomains: List<String> = emptyList(),
    val unlistedHmacCount: Int = 0,
    val selectedTab: ExceptionTab = ExceptionTab.ALLOW,
    val searchQuery: String = "",
    val testResult: FilterResult? = null,
)

/**
 * Coordinates readable `user_rule` exceptions, My domains protection list, and live rule testing (WP-20 / F29).
 */
@HiltViewModel
class ExceptionsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        userRuleDao: UserRuleDao,
        private val userRuleGateway: UserRuleGateway,
    ) : ViewModel() {
        private val myDomainsStore = MyDomainsStore.getInstance(appContext)
        private val selectedTab = MutableStateFlow(ExceptionTab.ALLOW)
        private val searchQuery = MutableStateFlow("")
        private val testResult = MutableStateFlow<FilterResult?>(null)
        private val hmacRefreshTrigger = MutableStateFlow(0)

        private val filterEngine: ContentFilterEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ContentFilterEngine(appContext)
        }

        private val _feedbackMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)

        /** Transient validation or mutation feedback emitted for snackbars. */
        val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

        /** Reactive UI state combining Room `user_rule` rows, My domains, filters, and test results. */
        val uiState: StateFlow<ExceptionsUiState> =
            combine(
                userRuleDao.observeAll(),
                myDomainsStore.domains,
                selectedTab,
                searchQuery,
                testResult,
            ) { allRules, protectedSet, tab, query, tested ->
                val allowAll = allRules.filter { it.action == UserRuleEntity.ACTION_ALLOW }
                val blockAll = allRules.filter { it.action == UserRuleEntity.ACTION_BLOCK }
                val hmacTotal =
                    DeviceLearning.allowlist(appContext).size() + DeviceLearning.blocklist(appContext).size()
                ExceptionsUiState(
                    allowRules = filterRuleList(allowAll, query),
                    blockRules = filterRuleList(blockAll, query),
                    totalAllowCount = allowAll.size,
                    totalBlockCount = blockAll.size,
                    protectedDomains = filterDomainSet(protectedSet, query),
                    unlistedHmacCount = (hmacTotal - allRules.size).coerceAtLeast(0),
                    selectedTab = tab,
                    searchQuery = query,
                    testResult = tested,
                )
            }.combine(hmacRefreshTrigger) { state, _ -> state }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ExceptionsUiState(),
                )

        /** Selects the Allow or Block tab. */
        fun selectTab(tab: ExceptionTab) {
            selectedTab.value = tab
        }

        /** Updates the search query filtering exceptions and protected domains. */
        fun setSearchQuery(query: String) {
            searchQuery.value = query
        }

        /**
         * Evaluates [rawDomain] against the live on-device [ContentFilterEngine].
         */
        fun testDomain(rawDomain: String) {
            val trimmed = rawDomain.trim()
            if (trimmed.isEmpty()) {
                testResult.value = null
                return
            }
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.Default) {
                        filterEngine.analyzeAndFilter(trimmed)
                    }
                testResult.value = result
            }
        }

        /**
         * Adds a user allow or block exception across both HMAC stores and the readable `user_rule` table.
         */
        fun addRule(
            rawDomain: String,
            isBlock: Boolean,
            wholeSite: Boolean,
        ) {
            val feedback = if (isBlock) UserFeedback.BLOCK else UserFeedback.ALLOW
            viewModelScope.launch {
                val result =
                    if (wholeSite) {
                        userRuleGateway.setWholeSite(rawDomain, feedback)
                    } else {
                        userRuleGateway.set(rawDomain, feedback)
                    }
                when (result) {
                    RuleUpdateResult.Applied -> {
                        selectedTab.value = if (isBlock) ExceptionTab.BLOCK else ExceptionTab.ALLOW
                        hmacRefreshTrigger.value++
                        DomainName.normalizeUserEntry(rawDomain)?.let { normalized ->
                            testResult.value =
                                withContext(Dispatchers.Default) {
                                    filterEngine.analyzeAndFilter(normalized)
                                }
                        }
                    }

                    is RuleUpdateResult.Failed -> {
                        _feedbackMessage.emit(result.reason)
                    }
                }
            }
        }

        /**
         * Deletes an exception across both `user_rule` and HMAC stores.
         */
        fun removeRule(domain: String) {
            viewModelScope.launch {
                when (val result = userRuleGateway.reset(domain)) {
                    RuleUpdateResult.Applied -> hmacRefreshTrigger.value++
                    is RuleUpdateResult.Failed -> _feedbackMessage.emit(result.reason)
                }
            }
        }

        /**
         * Pure helper filtering rule entities by case-insensitive substring match.
         */
        companion object {
            fun filterRuleList(
                rules: List<UserRuleEntity>,
                query: String,
            ): List<UserRuleEntity> {
                val needle = query.trim().lowercase(Locale.ROOT)
                if (needle.isEmpty()) return rules
                return rules.filter { it.domain.contains(needle, ignoreCase = true) }
            }

            fun filterDomainSet(
                domains: Set<String>,
                query: String,
            ): List<String> {
                val needle = query.trim().lowercase(Locale.ROOT)
                val sorted = domains.sorted()
                if (needle.isEmpty()) return sorted
                return sorted.filter { it.contains(needle, ignoreCase = true) }
            }
        }
    }
