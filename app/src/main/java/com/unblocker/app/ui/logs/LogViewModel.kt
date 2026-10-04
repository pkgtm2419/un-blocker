package com.unblocker.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.data.repository.FilterRepository
import com.unblocker.data.repository.LogRepository
import com.unblocker.data.db.entity.DnsLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard / False-Positive screen.
 *
 * Exposes a reactive [recentLogs] flow to the Compose UI.
 * Handles the user action to whitelist a domain, which updates the database
 * and instantly hot-swaps the Bloom Filter via the [FilterRepository].
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    logRepository: LogRepository,
    private val filterRepository: FilterRepository
) : ViewModel() {

    /**
     * The last 100 DNS queries intercepted by the VPN.
     * Starts lazily; stops observing 5 seconds after the UI disappears.
     */
    val recentLogs: StateFlow<List<DnsLogEntity>> = logRepository.recentLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * User-initiated override: Unblocks a domain that was incorrectly flagged.
     *
     * 1. Adds domain to the whitelist table.
     * 2. Deletes the domain from the block_rules table.
     * 3. Rebuilds the in-memory Bloom Filter.
     * (All handled atomically inside FilterRepository).
     */
    fun unblockDomain(domain: String) {
        viewModelScope.launch {
            filterRepository.whitelistDomain(domain)
        }
    }
}
