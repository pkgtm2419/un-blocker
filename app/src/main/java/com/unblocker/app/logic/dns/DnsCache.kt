package com.unblocker.app.logic.dns

import java.util.concurrent.ConcurrentHashMap

/**
 * Fast in-memory DNS cache to accelerate allowed domain queries
 * and avoid repeated network round-trips for high-traffic sites (e.g. YouTube, Google).
 */
class DnsCache(
    private val maxEntries: Int = 1000,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private data class CacheKey(val domain: String, val queryType: Short, val queryClass: Short)

    private data class CachedRecord(
        val dnsPayload: ByteArray,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<CacheKey, CachedRecord>()

    fun get(domain: String, queryType: Short, transactionId: Short, queryClass: Short = 1): ByteArray? {
        val key = CacheKey(domain, queryType, queryClass)
        val record = cache[key] ?: return null

        val now = nowMillis()
        if (now >= record.expiresAt) {
            cache.remove(key)
            return null
        }

        // Clone payload and inject current query transactionId
        val payload = record.dnsPayload.copyOf()
        if (payload.size >= 2) {
            payload[0] = (transactionId.toInt() shr 8).toByte()
            payload[1] = (transactionId.toInt() and 0xFF).toByte()
        }
        return payload
    }

    fun put(domain: String, queryType: Short, dnsPayload: ByteArray, ttlSeconds: Long,
        queryClass: Short = 1) {
        if (ttlSeconds <= 0) return
        if (cache.size >= maxEntries) {
            val now = nowMillis()
            val expiredKeys = cache.entries.filter { it.value.expiresAt < now }.map { it.key }
            for (k in expiredKeys) {
                cache.remove(k)
            }
            if (cache.size >= maxEntries) {
                val keysToRemove = cache.entries.take(50).map { it.key }
                for (k in keysToRemove) {
                    cache.remove(k)
                }
            }
        }

        val key = CacheKey(domain, queryType, queryClass)
        val expiresAt = nowMillis() + (ttlSeconds.coerceAtMost(600) * 1000L)
        cache[key] = CachedRecord(dnsPayload.copyOf(), expiresAt)
    }

    fun clear() {
        cache.clear()
    }
}
