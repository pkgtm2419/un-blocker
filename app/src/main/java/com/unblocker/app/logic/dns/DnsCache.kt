package com.unblocker.app.logic.dns

import java.util.concurrent.ConcurrentHashMap

/**
 * Fast in-memory DNS cache to accelerate allowed domain queries
 * and avoid repeated network round-trips for high-traffic sites (e.g. YouTube, Google).
 */
class DnsCache(private val maxEntries: Int = 1000) {

    private data class CachedRecord(
        val dnsPayload: ByteArray,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<String, CachedRecord>()

    fun get(domain: String, queryType: Short, transactionId: Short): ByteArray? {
        val key = "$domain:$queryType"
        val record = cache[key] ?: return null

        val now = System.currentTimeMillis()
        if (now > record.expiresAt) {
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

    fun put(domain: String, queryType: Short, dnsPayload: ByteArray, ttlSeconds: Long = 120) {
        if (cache.size >= maxEntries) {
            val now = System.currentTimeMillis()
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

        val key = "$domain:$queryType"
        val expiresAt = System.currentTimeMillis() + (ttlSeconds.coerceIn(30, 600) * 1000L)
        cache[key] = CachedRecord(dnsPayload.copyOf(), expiresAt)
    }

    fun clear() {
        cache.clear()
    }
}
