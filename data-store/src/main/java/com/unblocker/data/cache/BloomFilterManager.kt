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
 * In-memory Bloom Filter for zero-latency domain blocking decisions on the VPN hot path.
 *
 * ## Design
 * - Uses Guava's [BloomFilter] backed by a compact [com.google.common.hash.BitArray].
 * - An [AtomicReference] holds the active filter, enabling **lock-free reads** from any
 *   number of concurrent packet-processing threads.
 * - Rebuilds (compiles) are serialized by [rebuildMutex] and atomically hot-swapped,
 *   so packet-processing threads never block and always read a complete filter.
 * - Subdomain / wildcard matching walks the domain hierarchy:
 *   `sub.ad.example.com` → `ad.example.com` → `example.com` → `com`
 *
 * ## Memory footprint
 * At the default 200,000 expected insertions and 0.1% FPP:
 *   ~**287 KB** (well within the Android process's typical 256 MB heap).
 *
 * ## Accuracy
 * False positives (a non-ad domain incorrectly reported as blocked) are bounded by [FPP].
 * False negatives are impossible: if a domain was inserted, [isBlocked] always returns true.
 *
 * @param blockRuleDao  DAO to load all blocked domains from Room.
 * @param whitelistDao  DAO to load user-whitelisted domains to exclude from the filter.
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
        private const val FPP = 0.001
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
     * Checks whether [domain] (or any of its parent domains) is in the active filter.
     *
     * Called on every DNS query intercepted by the VPN service — must be **non-blocking**
     * and complete in O(k) time where k is a small constant (Bloom Filter hash functions).
     *
     * @param domain  Lowercase, fully-qualified domain (e.g. "sub.ads.example.com").
     * @return        True if the domain should be blocked (may include false positives < FPP).
     */
    fun isBlocked(domain: String): Boolean {
        val filter = activeFilter.get() ?: return false

        // 1. Exact match
        if (filter.mightContain(domain)) return true

        // 2. Walk parent domains for wildcard / suffix matching
        var sub = domain
        while (sub.contains('.')) {
            sub = sub.substringAfter('.')
            if (filter.mightContain(sub)) return true
        }
        return false
    }

    /**
     * Inserts [domain] into the active filter immediately (no I/O).
     *
     * Used for fast user-whitelist-reversal: if a whitelist entry is removed, the domain
     * is re-added to the filter instantly without a full rebuild.
     */
    fun addDomain(domain: String) {
        activeFilter.get()?.put(domain)
    }

    /**
     * Asynchronous rebuild: loads all blocked domains from Room (excluding whitelisted ones),
     * compiles a new Bloom Filter, and atomically hot-swaps it.
     *
     * **Callers must invoke this from a coroutine on [kotlinx.coroutines.Dispatchers.IO].**
     *
     * Called after:
     * - App startup (initial load)
     * - Nightly ML batch worker completes
     * - User whitelists or un-whitelists a domain
     */
    suspend fun reloadFilterFromDatabase() = rebuildMutex.withLock {
        val blockedDomains = blockRuleDao.getAllBlockedDomains().toHashSet()
        val whitelistedDomains = whitelistDao.getAllWhitelistedDomains().toHashSet()

        // Exclude whitelisted domains — they always pass through regardless of block rules
        val effectiveDomains = blockedDomains - whitelistedDomains

        val capacity = maxOf(EXPECTED_INSERTIONS, effectiveDomains.size * 2)
        val newFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            capacity,
            FPP
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
            FPP
        )
}
