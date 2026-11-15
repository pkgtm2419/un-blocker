package com.unblocker.app.data.preferences

import android.content.Context
import com.unblocker.app.logic.DomainName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Stores up to 500 readable protected-site names in private SharedPreferences.
 *
 * This list is separate from the HMAC-backed user rule and learning stores.
 *
 * @param context Context used to access the private My domains preference file.
 */
class MyDomainsStore(context: Context) {
    private val prefs = context.getSharedPreferences("my_domains_store", Context.MODE_PRIVATE)
    private val _domains = MutableStateFlow<Set<String>>(emptySet())
    /**
     * Current readable protected-site names as a read-only set snapshot.
     */
    val domains: StateFlow<Set<String>> = _domains.asStateFlow()

    init {
        loadDomains()
    }

    private fun loadDomains() {
        val raw = prefs.getStringSet(KEY_MY_DOMAINS, emptySet()) ?: emptySet()
        _domains.value = Collections.unmodifiableSet(raw)
    }

    /**
     * Normalizes and adds one protected host if capacity remains.
     *
     * @param rawDomain User-entered host to normalize.
     * @return True only when a new entry was added; invalid, duplicate, or over-capacity input returns false.
     */
    fun addDomain(rawDomain: String): Boolean {
        val normalized = DomainName.normalizeUserEntry(rawDomain) ?: return false
        val current = _domains.value.toMutableSet()
        if (current.size >= MAX_PROTECTED_DOMAINS) return false
        val added = current.add(normalized)
        if (added) {
            prefs.edit().putStringSet(KEY_MY_DOMAINS, current).apply()
            _domains.value = Collections.unmodifiableSet(current)
        }
        return added
    }

    /**
     * Removes a normalized protected host if present.
     *
     * @param rawDomain Host to remove; malformed input falls back to trimmed lowercase text.
     * @return Whether an entry was removed.
     */
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

    /**
     * Checks exact membership in the protected-host set.
     *
     * @param rawDomain Host to normalize, with trimmed lowercase fallback.
     * @return Whether the exact normalized entry is stored.
     */
    fun contains(rawDomain: String): Boolean {
        val normalized = DomainName.normalize(rawDomain) ?: rawDomain.trim().lowercase()
        return _domains.value.contains(normalized)
    }

    /**
     * Adds valid, unique hosts in input order until the 500-entry capacity is reached.
     *
     * @param rawList User-entered hosts to normalize and import.
     * @return Number of entries newly added.
     */
    fun importDomains(rawList: List<String>): Int {
        var addedCount = 0
        val current = _domains.value.toMutableSet()
        for (item in rawList) {
            if (current.size >= MAX_PROTECTED_DOMAINS) break
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

    /**
     * Removes all protected-site names from preferences and the published snapshot.
     */
    fun clearAll() {
        prefs.edit().remove(KEY_MY_DOMAINS).apply()
        _domains.value = emptySet()
    }

    /**
     * Protected-site capacity, preference key, and application-scoped store accessor.
     */
    companion object {
        private const val MAX_PROTECTED_DOMAINS = 500
        private const val KEY_MY_DOMAINS = "my_domains_list"

        @Volatile
        private var INSTANCE: MyDomainsStore? = null

        /**
         * Returns the process-wide My domains store.
         *
         * @param context Context whose application context is retained.
         * @return Shared readable protected-site store.
         */
        fun getInstance(context: Context): MyDomainsStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MyDomainsStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
