package com.unblocker.app.ui.extensions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.R
import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.data.model.ExtensionPreset
import com.unblocker.app.data.model.FilterCategories
import com.unblocker.app.data.repository.ExtensionRepository
import com.unblocker.app.services.ExtensionUpdateWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns catalog search, category selection, update actions, and transient extension messages.
 *
 * @param application Application used for the repository and periodic update scheduling.
 */
class ExtensionsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ExtensionRepository.getInstance(application)

    /**
     * Complete extension catalog published by the repository.
     */
    val extensions: StateFlow<List<ExtensionFilter>> = repository.extensions

    /**
     * Whether the repository reports an update operation in progress.
     */
    val isUpdating: StateFlow<Boolean> = repository.isUpdating

    /**
     * Persisted preference for periodic extension updates.
     */
    val autoUpdateEnabled: StateFlow<Boolean> = repository.autoUpdateEnabled

    /**
     * Currently active curated preset, or null if individual filter lists have been customized.
     */
    val appliedPreset: StateFlow<ExtensionPreset?> = repository.appliedPreset

    private val _searchQuery = MutableStateFlow("")

    /**
     * Current extension search text.
     */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FilterCategories.ALL)

    /**
     * Currently selected catalog grouping label.
     */
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()

    /**
     * Transient user-visible messages from extension actions.
     */
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    /**
     * Catalog entries matching the current category and case-insensitive search text.
     */
    val filteredExtensions: StateFlow<List<ExtensionFilter>> =
        combine(
            extensions,
            searchQuery,
            selectedCategory,
        ) { list, query, category ->
            val byCategory =
                when (category) {
                    FilterCategories.ALL -> {
                        list
                    }

                    FilterCategories.RECOMMENDED -> {
                        list.filter {
                            it.category.equals(FilterCategories.RECOMMENDED, ignoreCase = true) ||
                                it.id in RECOMMENDED_EXTENSIONS
                        }
                    }

                    else -> {
                        list.filter { it.category.equals(category, ignoreCase = true) }
                    }
                }

            if (query.isBlank()) {
                byCategory
            } else {
                byCategory.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.url.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Number of enabled catalog entries, regardless of whether cached rules are present.
     */
    val activeFilterCount: StateFlow<Int> =
        extensions
            .map { list ->
                list.count { it.isEnabled }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Sum of recorded rule counts for enabled entries; this is not a deduplicated runtime rule count.
     */
    val totalEnabledRulesCount: StateFlow<Int> =
        extensions
            .map { list ->
                list.filter { it.isEnabled }.sumOf { it.filterCount }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Updates the extension search text.
     *
     * @param query Text matched against names, URLs, descriptions, and categories.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Updates the selected extension category.
     *
     * @param category Catalog label used to filter entries.
     */
    fun onCategorySelect(category: String) {
        _selectedCategory.value = category
    }

    /**
     * Updates one extension's enabled state and schedules active-rule rebuilding.
     *
     * @param id Catalog entry identifier.
     * @param enabled Whether cached rules from this entry should be active.
     */
    fun toggleExtension(
        id: String,
        enabled: Boolean,
    ) {
        repository.toggleExtension(id, enabled)
    }

    /**
     * Updates every extension's enabled state.
     *
     * @param enabled Whether all entries should be enabled.
     */
    fun setAllEnabled(enabled: Boolean) {
        repository.setAllEnabled(enabled)
    }

    /**
     * Applies a curated filter preset, updating enabled lists to match target coverage.
     *
     * @param preset The curated preset to activate.
     */
    fun applyPreset(preset: ExtensionPreset) {
        repository.applyPreset(preset)
        val app = getApplication<Application>()
        viewModelScope.launch {
            _snackbarMessage.emit(
                app.getString(R.string.extensions_snackbar_preset_activated, app.getString(preset.titleRes)),
            )
        }
    }

    /**
     * Persists automatic update intent and schedules or cancels the periodic worker.
     *
     * @param enabled Whether periodic checks should be requested.
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        repository.setAutoUpdateEnabled(enabled)
        val app = getApplication<Application>()
        if (enabled) {
            ExtensionUpdateWorker.schedule(app)
            viewModelScope.launch {
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_auto_update_on))
            }
        } else {
            ExtensionUpdateWorker.cancel(app)
            viewModelScope.launch {
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_auto_update_off))
            }
        }
    }

    /**
     * Launches updates for all entries with URLs and emits an aggregate result message.
     */
    fun updateAll() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_updating_all))
            val result = repository.updateAll()
            if (result.isSuccess) {
                val count = result.getOrThrow()
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_updated_all, count))
            } else {
                val reason = result.exceptionOrNull()?.message ?: app.getString(R.string.extensions_snackbar_network_error)
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_update_failed, reason))
            }
        }
    }

    /**
     * Launches one extension update and emits a result message. Missing IDs are ignored.
     *
     * @param id Catalog entry identifier to update.
     */
    fun updateExtension(id: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val extension = extensions.value.find { it.id == id } ?: return@launch
            _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_updating_one, extension.name))
            val result = repository.updateExtension(id)
            if (result.isSuccess) {
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_updated_one, extension.name))
            } else {
                _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_failed_one, extension.name))
            }
        }
    }

    /**
     * Adds custom metadata when the name is nonblank, without downloading its URL.
     *
     * @param name User-visible list name.
     * @param url Source URL saved for a later update attempt.
     */
    fun addCustomFilter(
        name: String,
        url: String,
    ) {
        if (name.isBlank()) return
        val app = getApplication<Application>()
        repository.addCustomFilter(name, url)
        viewModelScope.launch {
            _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_added_custom, name))
        }
    }

    /**
     * Removes matching metadata and cached rules and emits a completion message.
     *
     * @param id Catalog identifier passed to the repository for removal.
     */
    fun removeCustomFilter(id: String) {
        val app = getApplication<Application>()
        repository.removeCustomFilter(id)
        viewModelScope.launch {
            _snackbarMessage.emit(app.getString(R.string.extensions_snackbar_removed_custom))
        }
    }

    /**
     * Catalog identifiers used by the recommended category filter.
     */
    companion object {
        /**
         * Catalog identifiers treated as recommended in addition to entries explicitly carrying that category.
         */
        val RECOMMENDED_EXTENSIONS =
            setOf(
                "ublock_unbreak",
                "adguard_mobile",
                "easyprivacy",
                "hagezi_light_oem",
                "malware_urlhaus_c2",
                "oisd_small",
            )
    }
}
