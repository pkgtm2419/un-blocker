package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DnsCacheTest {

    private lateinit var cache: DnsCache

    @Before
    fun setup() {
        cache = DnsCache(maxEntries = 50)
    }

    @Test
    fun testCachePutAndGetWithTransactionIdRewrite() {
        val domain = "www.youtube.com"
        val queryType: Short = 1 // A
        val initialPayload = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01)

        cache.put(domain, queryType, initialPayload, ttlSeconds = 60)

        // Query with a different transaction ID
        val newTxId: Short = 0x5678
        val cached = cache.get(domain, queryType, newTxId)

        assertNotNull("Cached response should not be null", cached)
        assertEquals("Transaction ID high byte should be rewritten", 0x56.toByte(), cached!![0])
        assertEquals("Transaction ID low byte should be rewritten", 0x78.toByte(), cached[1])
        assertEquals("Flags should match original", 0x81.toByte(), cached[2])
    }

    @Test
    fun testCacheMiss() {
        val result = cache.get("nonexistent.domain.com", 1, 0x1111)
        assertNull("Cache miss should return null", result)
    }
}
