package com.unblocker.data.cache

import java.io.File
import java.io.FileInputStream
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance off-heap binary rule engine.
 *
 * Evaluates domain queries against a memory-mapped 64-bit FNV-1a binary index (`rules.bin`).
 * Achieves O(log2 N) lookup times (~20 comparisons for 686k rules) with zero JVM heap allocations
 * and zero GC pauses.
 */
@Singleton
class OffHeapRuleEngine @Inject constructor() {

    private val bufferRef = AtomicReference<MappedByteBuffer?>(null)

    /**
     * Memory-maps [indexFile] and atomically swaps the reference without lock contention.
     * If the file does not exist or contains less than 8 bytes, the engine resets to an empty state.
     */
    fun reloadFromFile(indexFile: File) {
        if (!indexFile.exists() || indexFile.length() < 8L) {
            bufferRef.set(null)
            return
        }

        runCatching {
            FileInputStream(indexFile).use { fis ->
                val channel = fis.channel
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, indexFile.length()).apply {
                    order(ByteOrder.BIG_ENDIAN)
                }
                bufferRef.set(mapped)
            }
        }.onFailure {
            bufferRef.set(null)
        }
    }

    /**
     * Checks if a 64-bit domain hash exists in the sorted index using allocation-free binary search.
     *
     * @param domainHash The 64-bit FNV-1a hash of the domain or subdomain to check.
     * @return True if the hash is present in the blocklist index.
     */
    fun isHashBlocked(domainHash: Long): Boolean {
        val buffer = bufferRef.get() ?: return false
        val totalEntries = buffer.capacity() / 8
        if (totalEntries == 0) return false

        var low = 0
        var high = totalEntries - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = buffer.getLong(mid * 8)
            if (midVal < domainHash) {
                low = mid + 1
            } else if (midVal > domainHash) {
                high = mid - 1
            } else {
                return true
            }
        }
        return false
    }

    /**
     * Checks if [domain] or any of its parent domain suffixes are blocked.
     *
     * Walks labels from left to right without allocating any substring or string objects.
     * For example, checking "a.b.doubleclick.net" tests:
     * 1. "a.b.doubleclick.net" (startIdx = 0)
     * 2. "b.doubleclick.net" (startIdx = 2)
     * 3. "doubleclick.net" (startIdx = 4)
     * 4. "net" (startIdx = 16)
     *
     * @param domain The target domain as a CharSequence.
     * @return True if [domain] or an enclosing parent domain is blocked.
     */
    fun isDomainBlocked(domain: CharSequence): Boolean {
        if (domain.isEmpty()) return false

        // 1. Check exact full domain
        val fullHash = RuleCompiler.fnv1a64(domain, 0, domain.length)
        if (isHashBlocked(fullHash)) return true

        // 2. Walk domain labels from left to right at each dot separator
        val len = domain.length
        for (i in 0 until len) {
            if (domain[i] == '.' && i + 1 < len) {
                val subHash = RuleCompiler.fnv1a64(domain, i + 1, len)
                if (isHashBlocked(subHash)) return true
            }
        }

        return false
    }

    /**
     * Returns the total number of unique 64-bit rules currently mapped into memory.
     */
    fun ruleCount(): Int {
        val buffer = bufferRef.get() ?: return 0
        return buffer.capacity() / 8
    }

    /**
     * Returns true if an off-heap index is currently mapped and ready for lookups.
     */
    fun isLoaded(): Boolean = bufferRef.get() != null
}
