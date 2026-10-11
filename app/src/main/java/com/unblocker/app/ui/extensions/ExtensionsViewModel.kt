package com.unblocker.app.ui.extensions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.model.ExtensionFilter
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

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ExtensionRepository.getInstance(application)

    val extensions: StateFlow<List<ExtensionFilter>> = repository.extensions
    val isUpdating: StateFlow<Boolean> = repository.isUpdating
    val autoUpdateEnabled: StateFlow<Boolean> = repository.autoUpdateEnabled

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FilterCategories.ALL)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val filteredExtensions: StateFlow<List<ExtensionFilter>> = combine(
        extensions,
        searchQuery,
        selectedCategory
    ) { list, query, category ->
        val byCategory = when (category) {
            FilterCategories.ALL -> list
            FilterCategories.RECOMMENDED -> list.filter {
                it.category.equals(FilterCategories.RECOMMENDED, ignoreCase = true) ||
                it.id in RECOMMENDED_EXTENSIONS
            }
            else -> list.filter { it.category.equals(category, ignoreCase = true) }
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

    val activeFilterCount: StateFlow<Int> = extensions.map { list ->
        list.count { it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalEnabledRulesCount: StateFlow<Int> = extensions.map { list ->
        list.filter { it.isEnabled }.sumOf { it.filterCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelect(category: String) {
        _selectedCategory.value = category
    }

    fun toggleExtension(id: String, enabled: Boolean) {
        repository.toggleExtension(id, enabled)
    }

    fun setAllEnabled(enabled: Boolean) {
        repository.setAllEnabled(enabled)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        repository.setAutoUpdateEnabled(enabled)
        if (enabled) {
            ExtensionUpdateWorker.schedule(getApplication())
            viewModelScope.launch {
                _snackbarMessage.emit("Auto-update enabled (periodic check scheduled)")
            }
        } else {
            ExtensionUpdateWorker.cancel(getApplication())
            viewModelScope.launch {
                _snackbarMessage.emit("Auto-update disabled")
            }
        }
    }

    fun updateAll() {
        viewModelScope.launch {
            _snackbarMessage.emit("Updating all extensions from internet…")
            val result = repository.updateAll()
            if (result.isSuccess) {
                val count = result.getOrThrow()
                _snackbarMessage.emit("Successfully updated $count extension(s)")
            } else {
                _snackbarMessage.emit("Update failed: ${result.exceptionOrNull()?.message ?: "Network error"}")
            }
        }
    }

    fun updateExtension(id: String) {
        viewModelScope.launch {
            val ext = extensions.value.find { it.id == id } ?: return@launch
            _snackbarMessage.emit("Updating ${ext.name}…")
            val result = repository.updateExtension(id)
            if (result.isSuccess) {
                _snackbarMessage.emit("Successfully updated ${ext.name}")
            } else {
                _snackbarMessage.emit("Failed to update ${ext.name}")
            }
        }
    }

    fun addCustomFilter(name: String, url: String) {
        if (name.isBlank()) return
        repository.addCustomFilter(name, url)
        viewModelScope.launch {
            _snackbarMessage.emit("Added filter: $name")
        }
    }

    fun removeCustomFilter(id: String) {
        repository.removeCustomFilter(id)
        viewModelScope.launch {
            _snackbarMessage.emit("Removed custom filter")
        }
    }

    companion object {
        val RECOMMENDED_EXTENSIONS = setOf(
            "adguard_dns",
            "hagezi_multi_light",
            "oisd_small",
            "adaway_default",
            "threatfox_malware",
            "stalkerware_indicators",
            "urlhaus_agh",
            "malicious_url_blocklist",
            "phishing_army_extended",
            "hagezi_native_samsung",
            "hagezi_native_xiaomi",
            "smarttv_perflyst",
            "firebog_wally3k",
            "firebog_admiral",
            "adguard_url_tracking",
            "easylist",
            "adguard_base",
            "easyprivacy",
            "onehosts_lite",
            "1dm_filters"
        )
    }
}
