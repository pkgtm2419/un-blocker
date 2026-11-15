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
 * Memory-mapped lookup of a sorted, big-endian signed 64-bit FNV-1a rule index.
 *
 * A new mapped buffer is published atomically. Hash collisions are not disambiguated against
 * original domain strings, and the index format contains no separate validation header.
 */
@Singleton
class OffHeapRuleEngine @Inject constructor() {

    private companion object {
        const val HASH_BYTES = 8
    }

    private val bufferRef = AtomicReference<MappedByteBuffer?>(null)

    /**
     * Maps a rule file and replaces the current buffer.
     *
     * Missing files, files shorter than one hash, and caught mapping failures clear the current index.
     *
     * @param indexFile Sorted big-endian 64-bit hash file; trailing incomplete bytes are not entries.
     */
    fun reloadFromFile(indexFile: File) {
        if (!indexFile.exists() || indexFile.length() < HASH_BYTES.toLong()) {
            bufferRef.set(null)
            return
        }

        runCatching {
            FileInputStream(indexFile).use { inputStream ->
                val channel = inputStream.channel
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
        val totalEntries = buffer.capacity() / HASH_BYTES
        if (totalEntries == 0) return false

        var low = 0
        var high = totalEntries - 1

        while (low <= high) {
            val middleIndex = (low + high) ushr 1
            val middleHash = buffer.getLong(middleIndex * HASH_BYTES)
            if (middleHash < domainHash) {
                low = middleIndex + 1
            } else if (middleHash > domainHash) {
                high = middleIndex - 1
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
        val domainLength = domain.length
        for (i in 0 until domainLength) {
            if (domain[i] == '.' && i + 1 < domainLength) {
                val suffixHash = RuleCompiler.fnv1a64(domain, i + 1, domainLength)
                if (isHashBlocked(suffixHash)) return true
            }
        }

        return false
    }

    /**
     * Counts complete hash entries in the mapped buffer.
     *
     * @return Number of eight-byte entries, or zero when no index is loaded.
     */
    fun ruleCount(): Int {
        val buffer = bufferRef.get() ?: return 0
        return buffer.capacity() / HASH_BYTES
    }

    /**
     * Reports whether a buffer is mapped.
     *
     * @return True when a buffer is available; this does not validate its sorting or contents.
     */
    fun isLoaded(): Boolean = bufferRef.get() != null
}
