package com.unblocker.app.data.preferences

import android.content.Context
import com.unblocker.app.logic.DomainName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

class MyDomainsStore(context: Context) {
    private val prefs = context.getSharedPreferences("my_domains_store", Context.MODE_PRIVATE)
    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    val domains: StateFlow<Set<String>> = _domains.asStateFlow()

    init {
        loadDomains()
    }

    private fun loadDomains() {
        val raw = prefs.getStringSet(KEY_MY_DOMAINS, emptySet()) ?: emptySet()
        _domains.value = Collections.unmodifiableSet(raw)
    }

    fun addDomain(rawDomain: String): Boolean {
        val normalized = DomainName.normalizeUserEntry(rawDomain) ?: return false
        val current = _domains.value.toMutableSet()
        if (current.size >= 500) return false
        val added = current.add(normalized)
        if (added) {
            prefs.edit().putStringSet(KEY_MY_DOMAINS, current).apply()
            _domains.value = Collections.unmodifiableSet(current)
        }
        return added
    }

    fun removeDomain(rawDomain: String): Boolean {
        val normalized = DomainName.normalize(rawDomain) ?: rawDomain.trim().lowercase()
        val current = _domains.value.toMutableSet()
        val removed = current.remove(normalized)
        if (removed) {
            prefs.edit().putStringSet(KEY_MY_DOMAINS, current).apply()
            _domains.value = Collections.unmodifiableSet(current)
        }
        return removed
    }

    fun contains(rawDomain: String): Boolean {
        val normalized = DomainName.normalize(rawDomain) ?: rawDomain.trim().lowercase()
        return _domains.value.contains(normalized)
    }

    fun importDomains(rawList: List<String>): Int {
        var addedCount = 0
        val current = _domains.value.toMutableSet()
        for (item in rawList) {
            if (current.size >= 500) break
            val normalized = DomainName.normalizeUserEntry(item) ?: continue
            if (current.add(normalized)) {
                addedCount++
            }
        }
        if (addedCount > 0) {
            prefs.edit().putStringSet(KEY_MY_DOMAINS, current).apply()
            _domains.value = Collections.unmodifiableSet(current)
        }
        return addedCount
    }

    fun clearAll() {
        prefs.edit().remove(KEY_MY_DOMAINS).apply()
        _domains.value = emptySet()
    }

    companion object {
        private const val KEY_MY_DOMAINS = "my_domains_list"

        @Volatile
        private var INSTANCE: MyDomainsStore? = null

        fun getInstance(context: Context): MyDomainsStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MyDomainsStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
