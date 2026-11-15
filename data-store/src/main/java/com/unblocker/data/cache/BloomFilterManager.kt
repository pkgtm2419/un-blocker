package com.unblocker.data.cache

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory probabilistic domain filter with serialized database rebuilds and atomic publication.
 *
 * Lookups test the exact domain and each parent suffix, including the final label. A target
 * false-positive probability applies per Bloom lookup, not to the complete suffix-walking decision.
 * Rebuilds exclude exact whitelist keys; ancestor rules and probabilistic hits may still match.
 *
 * @param blockRuleDao Source of readable block-rule keys.
 * @param whitelistDao Source of exact allow keys excluded during rebuild.
 */
@Singleton
class BloomFilterManager @Inject constructor(
    private val blockRuleDao: BlockRuleDao,
    private val whitelistDao: WhitelistDao
) {
    companion object {
        /** Expected maximum number of domains in the block list. */
        private const val EXPECTED_INSERTIONS = 200_000

        /** Target false-positive probability (0.1%). */
        private const val FALSE_POSITIVE_PROBABILITY = 0.001
    }

    /**
     * Lock-free atomic reference to the live filter.
     * Readers (packet loop) call [AtomicReference.get] — no locking, no contention.
     */
    private val activeFilter = AtomicReference<BloomFilter<CharSequence>>(createEmptyFilter())

    /**
     * Serializes rebuild operations: only one coroutine rebuilds at a time,
     * but readers are never blocked.
     */
    private val rebuildMutex = Mutex()

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Tests the active filter without acquiring the rebuild mutex.
     *
     * @param domain Domain expected to be normalized by the caller.
     * @return Whether the domain or a parent suffix might be present; false positives are possible.
     */
    fun isBlocked(domain: String): Boolean {
        val filter = activeFilter.get() ?: return false

        // 1. Exact match
        if (filter.mightContain(domain)) return true

        // 2. Walk parent domains for wildcard / suffix matching
        var parentDomain = domain
        while (parentDomain.contains('.')) {
            parentDomain = parentDomain.substringAfter('.')
            if (filter.mightContain(parentDomain)) return true
        }
        return false
    }

    /**
     * Adds a key directly to the current filter without database I/O.
     *
     * A concurrent rebuild can replace this filter, so persistence requires a database mutation.
     *
     * @param domain Domain key to add exactly as supplied.
     */
    fun addDomain(domain: String) {
        activeFilter.get()?.put(domain)
    }

    /**
     * Loads block and allow keys, then atomically publishes a newly compiled filter.
     *
     * Rebuilds are serialized. Exact allow keys are excluded; this does not implement a separate
     * allow check during suffix lookups. Database failures propagate to the caller.
     */
    suspend fun reloadFilterFromDatabase() = rebuildMutex.withLock {
        val blockedDomains = blockRuleDao.getAllBlockedDomains().toHashSet()
        val whitelistedDomains = whitelistDao.getAllWhitelistedDomains().toHashSet()

        // Exclude exact allow keys from the new filter; suffix checks may still match an ancestor.
        val effectiveDomains = blockedDomains - whitelistedDomains

        val capacity = maxOf(EXPECTED_INSERTIONS, effectiveDomains.size * 2)
        val newFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            capacity,
            FALSE_POSITIVE_PROBABILITY
        )
        effectiveDomains.forEach { newFilter.put(it) }

        // Atomic hot-swap: all subsequent isBlocked() calls immediately use the new filter
        activeFilter.set(newFilter)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun createEmptyFilter(): BloomFilter<CharSequence> =
        BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            EXPECTED_INSERTIONS,
            FALSE_POSITIVE_PROBABILITY
        )
}
