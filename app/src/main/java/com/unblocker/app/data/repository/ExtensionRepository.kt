package com.unblocker.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.logic.rules.DnsRule
import com.unblocker.app.logic.rules.ExtensionRuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ExtensionRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("extension_preferences", Context.MODE_PRIVATE)

    private val extensionsDir = File(context.filesDir, "extensions").apply { mkdirs() }

    private val _extensions = MutableStateFlow<List<ExtensionFilter>>(emptyList())
    val extensions: StateFlow<List<ExtensionFilter>> = _extensions.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, false))
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    init {
        loadExtensions()
        reloadActiveRules()
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
        _autoUpdateEnabled.value = enabled
    }

    fun toggleExtension(id: String, enabled: Boolean) {
        val updated = _extensions.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        _extensions.value = updated
        persistExtensions(updated)
        reloadActiveRules()
    }

    fun setAllEnabled(enabled: Boolean) {
        val updated = _extensions.value.map { it.copy(isEnabled = enabled) }
        _extensions.value = updated
        persistExtensions(updated)
        reloadActiveRules()
    }

    fun addCustomFilter(name: String, url: String) {
        val id = "custom_" + System.currentTimeMillis()
        val customFilter = ExtensionFilter(
            id = id,
            name = name,
            url = url,
            filterCount = 0,
            sizeBytes = 0L,
            lastUpdatedEpochMs = System.currentTimeMillis(),
            isEnabled = true,
            isCustom = true
        )
        val updated = listOf(customFilter) + _extensions.value
        _extensions.value = updated
        persistExtensions(updated)
    }

    fun removeCustomFilter(id: String) {
        val updated = _extensions.value.filter { it.id != id }
        _extensions.value = updated
        persistExtensions(updated)
        val file = File(extensionsDir, "$id.rules")
        if (file.exists()) file.delete()
        reloadActiveRules()
    }

    fun reloadActiveRules() {
        try {
            val rules = mutableListOf<DnsRule>()

            // 1. Load bundled default rules if any enabled
            val anyEnabled = _extensions.value.any { it.isEnabled }
            if (anyEnabled) {
                runCatching {
                    context.assets.open("extensions/bundled_rules.txt").use { stream ->
                        BufferedReader(InputStreamReader(stream)).useLines { lines ->
                            for (line in lines) {
                                ExtensionRuleStore.parseLine(line)?.let { rules.add(it) }
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
                            ExtensionRuleStore.parseLine(line)?.let { rules.add(it) }
                        }
                    }
                }
            }

            ExtensionRuleStore.setRules(rules)
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    suspend fun updateAll(): Result<Int> = withContext(Dispatchers.IO) {
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
                    updatedList[i] = ext.copy(
                        filterCount = count,
                        sizeBytes = size,
                        lastUpdatedEpochMs = System.currentTimeMillis()
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

    suspend fun updateExtension(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
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
                currentList[index] = ext.copy(
                    filterCount = count,
                    sizeBytes = size,
                    lastUpdatedEpochMs = System.currentTimeMillis()
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

    private fun downloadAndSaveFilter(ext: ExtensionFilter): Result<Pair<Int, Long>> {
        return runCatching {
            var urlString = ext.url
            var connection: HttpURLConnection? = null
            var redirectCount = 0

            while (redirectCount < 5) {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 unblocker/2.0")
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: break
                    urlString = location
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            val conn = connection ?: error("Unable to establish connection")
            val targetFile = File(extensionsDir, "${ext.id}.rules.tmp")
            var validRulesCount = 0
            var totalBytes = 0L

            conn.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val writer = output.bufferedWriter()
                    reader.forEachLine { line ->
                        totalBytes += line.length + 1
                        if (ExtensionRuleStore.parseLine(line) != null) {
                            validRulesCount++
                            writer.write(line)
                            writer.newLine()
                        }
                    }
                    writer.flush()
                }
            }

            val finalFile = File(extensionsDir, "${ext.id}.rules")
            if (targetFile.exists()) {
                targetFile.renameTo(finalFile)
            }

            Pair(if (validRulesCount > 0) validRulesCount else ext.filterCount, totalBytes)
        }
    }

    private fun loadExtensions() {
        val savedJson = prefs.getString(KEY_EXTENSIONS_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                val list = parseJsonToExtensions(savedJson)
                if (list.isNotEmpty()) {
                    _extensions.value = list
                    return
                }
            }
        }

        // Load bundled manifest
        val list = loadBundledManifest()
        _extensions.value = list
        persistExtensions(list)
    }

    private fun loadBundledManifest(): List<ExtensionFilter> {
        return runCatching {
            context.assets.open("extensions/extensions_manifest.json").reader().use { reader ->
                val jsonObject = JSONObject(reader.readText())
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
                            syntax = obj.optString("syntax", "rules")
                        )
                    )
                }
                result
            }
        }.getOrDefault(emptyList())
    }

    private fun persistExtensions(list: List<ExtensionFilter>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
                put("filterCount", item.filterCount)
                put("sizeBytes", item.sizeBytes)
                put("lastUpdated", item.lastUpdatedEpochMs)
                put("enabled", item.isEnabled)
                put("custom", item.isCustom)
                put("syntax", item.syntax)
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
                    syntax = obj.optString("syntax", "rules")
                )
            )
        }
        return result
    }

    companion object {
        private const val KEY_EXTENSIONS_JSON = "key_extensions_json"
        private const val KEY_AUTO_UPDATE = "key_auto_update_extensions"

        @Volatile
        private var INSTANCE: ExtensionRepository? = null

        fun getInstance(context: Context): ExtensionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtensionRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
