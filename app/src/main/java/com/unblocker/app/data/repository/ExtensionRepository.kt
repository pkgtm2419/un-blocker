package com.unblocker.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.data.model.ExtensionPreset
import com.unblocker.app.data.model.FilterCategories
import com.unblocker.app.logic.rules.ExtensionRuleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL

/**
 * Most recently reported downloaded-rule admission state.
 */
sealed interface RuleUpdateState {
    /**
     * No downloaded-rule failure is currently reported.
     */
    data object Ready : RuleUpdateState

    /**
     * A downloaded list could not be installed.
     *
     * @property reason User-facing download or admission failure explanation.
     */
    data class Failed(
        val reason: String,
    ) : RuleUpdateState
}

/**
 * Owns extension metadata, cached rule files, and rebuilding of the active extension rule store.
 *
 * @param context Application context used for assets and private extension storage.
 */
class ExtensionRepository private constructor(
    private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("extension_preferences", Context.MODE_PRIVATE)

    private val extensionsDir = File(context.filesDir, "extensions").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _extensions = MutableStateFlow<List<ExtensionFilter>>(emptyList())

    /**
     * Current catalog entries, including enabled state and cached download metadata.
     */
    val extensions: StateFlow<List<ExtensionFilter>> = _extensions.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, false))

    /**
     * Persisted preference for periodic extension updates.
     */
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)

    /**
     * Whether an update operation is currently in progress.
     */
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _ruleUpdateState = MutableStateFlow<RuleUpdateState>(RuleUpdateState.Ready)

    /**
     * Last reported list download or admission state.
     */
    val ruleUpdateState: StateFlow<RuleUpdateState> = _ruleUpdateState.asStateFlow()

    private val _appliedPreset =
        MutableStateFlow<ExtensionPreset?>(
            prefs.getString(KEY_APPLIED_PRESET, null)?.let { name ->
                runCatching { ExtensionPreset.valueOf(name) }.getOrNull()
            },
        )

    /**
     * Currently active curated preset, or null if the user has customized individual lists.
     */
    val appliedPreset: StateFlow<ExtensionPreset?> = _appliedPreset.asStateFlow()

    private val reloadLock = Any()

    init {
        loadExtensions()
        reloadActiveRulesAsync()
    }

    /**
     * Schedules rebuilding the active rule store in the repository IO scope.
     */
    fun reloadActiveRulesAsync() {
        scope.launch {
            reloadActiveRules()
        }
    }

    /**
     * Persists the automatic-update preference; scheduling is managed by callers.
     *
     * @param enabled Whether periodic updates are requested.
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
        _autoUpdateEnabled.value = enabled
    }

    /**
     * Persists one entry's enabled state and schedules a rule-store rebuild.
     *
     * @param id Catalog identifier to update.
     * @param enabled Whether the entry should contribute cached rules.
     */
    fun toggleExtension(
        id: String,
        enabled: Boolean,
    ) {
        val updated =
            _extensions.value.map {
                if (it.id == id) it.copy(isEnabled = enabled) else it
            }
        _extensions.value = updated
        _appliedPreset.value = null
        prefs.edit().remove(KEY_APPLIED_PRESET).apply()
        persistExtensions(updated)
        reloadActiveRulesAsync()
    }

    /**
     * Persists the enabled state of every entry and schedules a rule-store rebuild.
     *
     * @param enabled Whether all catalog entries should be enabled.
     */
    fun setAllEnabled(enabled: Boolean) {
        val updated = _extensions.value.map { it.copy(isEnabled = enabled) }
        _extensions.value = updated
        _appliedPreset.value = null
        prefs.edit().remove(KEY_APPLIED_PRESET).apply()
        persistExtensions(updated)
        reloadActiveRulesAsync()
    }

    /**
     * Applies a curated extension preset, enabling target lists and disabling non-target ones.
     * User-created custom filters are preserved.
     *
     * @param preset The curated preset to apply.
     */
    fun applyPreset(preset: ExtensionPreset) {
        val updated = computePresetExtensions(_extensions.value, preset)
        _extensions.value = updated
        _appliedPreset.value = preset
        persistExtensions(updated)
        prefs.edit().putString(KEY_APPLIED_PRESET, preset.name).apply()
        reloadActiveRulesAsync()
    }

    /**
     * Adds enabled custom metadata without downloading the list.
     *
     * @param name Display name supplied by the user.
     * @param url List URL saved for a subsequent update attempt.
     */
    fun addCustomFilter(
        name: String,
        url: String,
    ) {
        val id = "custom_" + System.currentTimeMillis()
        val customFilter =
            ExtensionFilter(
                id = id,
                name = name,
                url = url,
                filterCount = 0,
                sizeBytes = 0L,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                isEnabled = true,
                isCustom = true,
                category = FilterCategories.CUSTOM,
                description = "User custom domain rules and exceptions",
            )
        val updated = listOf(customFilter) + _extensions.value
        _extensions.value = updated
        persistExtensions(updated)
    }

    /**
     * Removes matching metadata and its cached file, then schedules a rule-store rebuild.
     *
     * @param id Catalog identifier to remove.
     */
    fun removeCustomFilter(id: String) {
        val updated = _extensions.value.filter { it.id != id }
        _extensions.value = updated
        persistExtensions(updated)
        val file = File(extensionsDir, "$id.rules")
        if (file.exists()) file.delete()
        reloadActiveRulesAsync()
    }

    /**
     * Restores catalog enabled states and validated custom filters from a settings backup snapshot (WP-22).
     */
    fun restoreSelection(
        enabledCatalogIds: Set<String>,
        customFilters: List<com.unblocker.app.data.export.CustomExtensionBackup>,
    ) {
        val currentBuiltin =
            _extensions.value
                .filter { !it.isCustom }
                .map { it.copy(isEnabled = it.id in enabledCatalogIds) }
        val customEntries =
            customFilters.map { custom ->
                ExtensionFilter(
                    id = custom.id,
                    name = custom.name,
                    url = custom.url,
                    filterCount = 0,
                    sizeBytes = 0L,
                    lastUpdatedEpochMs = System.currentTimeMillis(),
                    isEnabled = custom.isEnabled,
                    isCustom = true,
                    category = FilterCategories.CUSTOM,
                    description = "Restored custom domain filter list",
                )
            }
        val combined = customEntries + currentBuiltin
        _extensions.value = combined
        _appliedPreset.value = null
        prefs.edit().remove(KEY_APPLIED_PRESET).apply()
        persistExtensions(combined)
        reloadActiveRulesAsync()
    }

    /**
     * Rebuilds active rules from bundled data and enabled cached lists. Unreadable sources are skipped.
     *
     * This performs file I/O on the calling thread; use [reloadActiveRulesAsync] from UI callers.
     */
    fun reloadActiveRules() {
        synchronized(reloadLock) {
            try {
                val builder = ExtensionRuleStore.Builder()

                // 1. Load bundled default rules if any enabled
                val anyEnabled = _extensions.value.any { it.isEnabled }
                if (anyEnabled) {
                    runCatching {
                        context.assets.open("extensions/bundled_rules.txt").use { stream ->
                            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                                for (line in lines) {
                                    builder.addLine(line)
                                }
                            }
                        }
                    }
                }

                // 2. Load cached rule files for all currently enabled extensions
                for (ext in _extensions.value) {
                    if (!ext.isEnabled) continue
                    val ruleFile = File(extensionsDir, "${ext.id}.rules")
                    if (ruleFile.exists()) {
                        runCatching {
                            ruleFile.forEachLine { line ->
                                builder.addLine(line)
                            }
                        }
                    }
                }

                ExtensionRuleStore.applyBuilder(builder)
            } catch (_: Exception) {
                // Safe fallback
            }
        }
    }

    /**
     * Attempts downloads for every entry with a URL, retaining metadata for failed downloads.
     *
     * @return Number of successful downloads, or a failure if the enclosing update operation fails.
     */
    suspend fun updateAll(): Result<Int> =
        withContext(Dispatchers.IO) {
            _isUpdating.value = true
            var successCount = 0
            try {
                val currentList = _extensions.value
                val updatedList = currentList.toMutableList()

                for (i in updatedList.indices) {
                    val ext = updatedList[i]
                    if (ext.url.isBlank()) continue

                    val result = downloadAndSaveFilter(ext)
                    if (result.isSuccess) {
                        val (count, size) = result.getOrThrow()
                        updatedList[i] =
                            ext.copy(
                                filterCount = count,
                                sizeBytes = size,
                                lastUpdatedEpochMs = System.currentTimeMillis(),
                            )
                        successCount++
                    }
                }

                _extensions.value = updatedList
                persistExtensions(updatedList)
                reloadActiveRules()
                Result.success(successCount)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _isUpdating.value = false
            }
        }

    /**
     * Downloads one entry and publishes updated metadata and active rules on success.
     *
     * @param id Catalog identifier to update.
     * @return Success containing true, or a failure for missing, invalid, or rejected entries.
     */
    suspend fun updateExtension(id: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            _isUpdating.value = true
            try {
                val currentList = _extensions.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == id }
                if (index == -1) return@withContext Result.failure(IllegalArgumentException("Extension not found"))

                val ext = currentList[index]
                if (ext.url.isBlank()) return@withContext Result.failure(IllegalArgumentException("URL is blank"))

                val result = downloadAndSaveFilter(ext)
                if (result.isSuccess) {
                    val (count, size) = result.getOrThrow()
                    currentList[index] =
                        ext.copy(
                            filterCount = count,
                            sizeBytes = size,
                            lastUpdatedEpochMs = System.currentTimeMillis(),
                        )
                    _extensions.value = currentList
                    persistExtensions(currentList)
                    reloadActiveRules()
                    Result.success(true)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
                }
            } finally {
                _isUpdating.value = false
            }
        }

    private suspend fun downloadAndSaveFilter(ext: ExtensionFilter): Result<Pair<Int, Long>> {
        val targetFile = File(extensionsDir, "${ext.id}.rules")
        val url =
            runCatching { URL(ext.url) }.getOrElse {
                val msg = "Invalid URL: ${ext.url}"
                _ruleUpdateState.value = RuleUpdateState.Failed(msg)
                return Result.failure(IllegalArgumentException(msg))
            }

        return when (val downloadResult = BoundedFilterDownloader.DEFAULT.download(url, targetFile)) {
            is DownloadResult.Success -> {
                _ruleUpdateState.value = RuleUpdateState.Ready
                Result.success(Pair(downloadResult.validRuleCount, downloadResult.bytesRead))
            }

            is DownloadResult.TooLarge -> {
                _ruleUpdateState.value = RuleUpdateState.Failed(downloadResult.message)
                Result.failure(IllegalStateException(downloadResult.message))
            }

            is DownloadResult.InvalidSource -> {
                _ruleUpdateState.value = RuleUpdateState.Failed(downloadResult.message)
                Result.failure(IllegalArgumentException(downloadResult.message))
            }

            is DownloadResult.HttpFailure -> {
                _ruleUpdateState.value = RuleUpdateState.Failed(downloadResult.message)
                Result.failure(java.io.IOException(downloadResult.message))
            }

            is DownloadResult.InvalidRules -> {
                _ruleUpdateState.value = RuleUpdateState.Failed(downloadResult.message)
                Result.failure(IllegalStateException(downloadResult.message))
            }

            is DownloadResult.NetworkFailure -> {
                _ruleUpdateState.value = RuleUpdateState.Failed(downloadResult.message)
                Result.failure(java.io.IOException(downloadResult.message))
            }
        }
    }

    private fun loadExtensions() {
        val savedJson = prefs.getString(KEY_EXTENSIONS_JSON, null)
        val savedVersion = prefs.getInt(KEY_MANIFEST_VERSION, 0)
        val (manifestVersion, bundledList) = loadBundledManifestWithVersion()

        if (savedJson.isNullOrBlank()) {
            _extensions.value = bundledList
            persistExtensions(bundledList)
            prefs.edit().putInt(KEY_MANIFEST_VERSION, manifestVersion).apply()
            return
        }

        val savedList = runCatching { parseJsonToExtensions(savedJson) }.getOrDefault(emptyList())

        if (savedVersion < manifestVersion) {
            val merged = migrateExtensions(savedList, bundledList)
            _extensions.value = merged
            persistExtensions(merged)
            prefs.edit().putInt(KEY_MANIFEST_VERSION, manifestVersion).apply()
        } else {
            _extensions.value = if (savedList.isNotEmpty()) savedList else bundledList
        }
    }

    private fun loadBundledManifestWithVersion(): Pair<Int, List<ExtensionFilter>> =
        runCatching {
            context.assets.open("extensions/extensions_manifest.json").reader().use { reader ->
                val jsonObject = JSONObject(reader.readText())
                val version = jsonObject.optInt("version", 1)
                val array = jsonObject.getJSONArray("extensions")
                val result = mutableListOf<ExtensionFilter>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        ExtensionFilter(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            url = obj.optString("url", ""),
                            filterCount = obj.optInt("filterCount", 0),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            lastUpdatedEpochMs = obj.optLong("lastUpdated", 0L),
                            isEnabled = obj.optBoolean("enabled", true),
                            isCustom = obj.optBoolean("custom", false),
                            syntax = obj.optString("syntax", "rules"),
                            category =
                                obj.optString(
                                    "category",
                                    if (obj.optBoolean(
                                            "custom",
                                            false,
                                        )
                                    ) {
                                        FilterCategories.CUSTOM
                                    } else {
                                        FilterCategories.PRIVACY
                                    },
                                ),
                            description = obj.optString("description", ""),
                            browserOnly = obj.optBoolean("browserOnly", false),
                        ),
                    )
                }
                Pair(version, result)
            }
        }.getOrDefault(Pair(1, emptyList()))

    private fun persistExtensions(list: List<ExtensionFilter>) {
        val array = JSONArray()
        for (item in list) {
            val obj =
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("url", item.url)
                    put("filterCount", item.filterCount)
                    put("sizeBytes", item.sizeBytes)
                    put("lastUpdated", item.lastUpdatedEpochMs)
                    put("enabled", item.isEnabled)
                    put("custom", item.isCustom)
                    put("syntax", item.syntax)
                    put("category", item.category)
                    put("description", item.description)
                    put("browserOnly", item.browserOnly)
                }
            array.put(obj)
        }
        prefs.edit().putString(KEY_EXTENSIONS_JSON, array.toString()).apply()
    }

    private fun parseJsonToExtensions(jsonStr: String): List<ExtensionFilter> {
        val array = JSONArray(jsonStr)
        val result = mutableListOf<ExtensionFilter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                ExtensionFilter(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.optString("url", ""),
                    filterCount = obj.optInt("filterCount", 0),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    lastUpdatedEpochMs = obj.optLong("lastUpdated", 0L),
                    isEnabled = obj.optBoolean("enabled", true),
                    isCustom = obj.optBoolean("custom", false),
                    syntax = obj.optString("syntax", "rules"),
                    category =
                        obj.optString(
                            "category",
                            if (obj.optBoolean("custom", false)) FilterCategories.CUSTOM else FilterCategories.PRIVACY,
                        ),
                    description = obj.optString("description", ""),
                    browserOnly = obj.optBoolean("browserOnly", false),
                ),
            )
        }
        return result
    }

    /**
     * Stable preference keys, catalog migration, and the application-scoped repository accessor.
     */
    companion object {
        /**
         * Preference key holding serialized extension metadata.
         */
        const val KEY_EXTENSIONS_JSON = "key_extensions_json"

        /**
         * Preference key holding the last incorporated bundled catalog version.
         */
        const val KEY_MANIFEST_VERSION = "key_manifest_version"

        /**
         * Preference key holding the name of the active curated preset.
         */
        const val KEY_APPLIED_PRESET = "key_applied_preset"
        private const val KEY_AUTO_UPDATE = "key_auto_update_extensions"

        /**
         * Merges a new bundled catalog with saved user selections and compatible cache metadata.
         *
         * @param savedList Previously persisted catalog, including user-created entries.
         * @param bundledList Current bundled definitions whose IDs and defaults take precedence.
         * @return Preserved custom entries followed by merged bundled entries.
         */
        fun migrateExtensions(
            savedList: List<ExtensionFilter>,
            bundledList: List<ExtensionFilter>,
        ): List<ExtensionFilter> {
            val bundledIds = bundledList.map { it.id }.toSet()
            val customFilters = savedList.filter { it.isCustom && it.id !in bundledIds }
            val savedMap = savedList.associateBy { it.id }

            val mergedBundled =
                bundledList.map { bundled ->
                    val saved =
                        savedMap[bundled.id]
                            ?: if (bundled.id == "oisd_small") savedMap["oisd_abp_full"] else null
                    if (saved != null) {
                        val isEnabled =
                            if (bundled.browserOnly && !bundled.isEnabled) {
                                false
                            } else {
                                saved.isEnabled
                            }
                        bundled.copy(
                            isEnabled = isEnabled,
                            filterCount = if (saved.filterCount > 0) saved.filterCount else bundled.filterCount,
                            sizeBytes = if (saved.sizeBytes > 0) saved.sizeBytes else bundled.sizeBytes,
                            lastUpdatedEpochMs =
                                if (saved.lastUpdatedEpochMs >
                                    0
                                ) {
                                    saved.lastUpdatedEpochMs
                                } else {
                                    bundled.lastUpdatedEpochMs
                                },
                        )
                    } else {
                        bundled
                    }
                }

            return customFilters + mergedBundled
        }

        /**
         * Computes the updated catalog list when applying a curated preset.
         * User-created custom filters are preserved unchanged.
         *
         * @param current The current list of extensions.
         * @param preset The curated preset to apply.
         * @param manufacturer The manufacturer string used for OEM target resolution.
         * @return New list with enabled states updated according to the preset targets.
         */
        fun computePresetExtensions(
            current: List<ExtensionFilter>,
            preset: ExtensionPreset,
            manufacturer: String = android.os.Build.MANUFACTURER,
        ): List<ExtensionFilter> {
            val targetIds = preset.resolveTargetIds(manufacturer)
            return current.map { filter ->
                if (filter.isCustom) {
                    filter
                } else {
                    filter.copy(isEnabled = filter.id in targetIds)
                }
            }
        }

        @Volatile
        private var INSTANCE: ExtensionRepository? = null

        /**
         * Returns the process-wide repository backed by application-private storage.
         *
         * @param context Context whose application context is retained.
         * @return Shared extension repository.
         */
        fun getInstance(context: Context): ExtensionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtensionRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
