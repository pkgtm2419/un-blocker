package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DnsCacheTest {

    @Test fun validatedCacheCopiesMetadataAndAgesTtlWithoutReparsing() {
        val query=DnsFixtures.query()
        val aliases=mutableListOf("tracker.test")
        val bytes=DnsFixtures.response(query,listOf(DnsFixtures.Record(query.domain,5,DnsFixtures.name("tracker.test"))))
        val parsed=com.unblocker.app.logic.dns.DnsResponseValidator.parseAndValidate(bytes,bytes.size,query)!!
        cache.putValidated(query,com.unblocker.app.services.ValidatedDnsResponse(bytes,parsed.copy(aliases=aliases)))
        aliases.clear(); bytes.fill(0)
        now+=2000
        val hit=cache.getValidated(query.copy(transactionId=0x5678))!!
        assertEquals(listOf("tracker.test"),hit.metadata.aliases)
        assertEquals(0x5678,hit.metadata.transactionId)
        val revalidated=com.unblocker.app.logic.dns.DnsResponseValidator.parseAndValidate(hit.bytes,hit.bytes.size,query.copy(transactionId=0x5678))!!
        assertEquals(58L,revalidated.minPositiveTtlSeconds)
        hit.bytes.fill(0)
        assertNotNull(cache.getValidated(query))
        now+=60000
        assertNull(cache.getValidated(query))
    }

    private lateinit var cache: DnsCache
    private var now = 1_000L

    @Before
    fun setup() {
        cache = DnsCache(maxEntries = 50, nowMillis = { now })
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

    @Test fun honorsResolverTtlIncludingZero() {
        val payload = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte())
        cache.put("short.example", 1, payload, ttlSeconds = 2)
        now = 2_999L
        assertNotNull(cache.get("short.example", 1, 0x1111))
        now = 3_001L
        assertNull(cache.get("short.example", 1, 0x1111))

        cache.put("zero.example", 1, payload, ttlSeconds = 0)
        assertNull(cache.get("zero.example", 1, 0x1111))
    }

    @Test fun dnsClassIsPartOfCacheIdentity() {
        val payload = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte())
        cache.put("class.example", 1, payload, ttlSeconds = 60, queryClass = 1)

        assertNotNull(cache.get("class.example", 1, 0x1111, queryClass = 1))
        assertNull(cache.get("class.example", 1, 0x1111, queryClass = 3))
    }

    @Test fun ipFamilyIsPartOfCacheIdentity() {
        val largeIpv6Payload = ByteArray(65_508).also {
            it[0] = 0x12
            it[1] = 0x34
        }
        cache.put(
            "large.example",
            1,
            largeIpv6Payload,
            ttlSeconds = 60,
            queryClass = 1,
            isIpv6 = true
        )

        assertNotNull(cache.get("large.example", 1, 0x1111, queryClass = 1, isIpv6 = true))
        assertNull(cache.get("large.example", 1, 0x1111, queryClass = 1, isIpv6 = false))
    }
}
