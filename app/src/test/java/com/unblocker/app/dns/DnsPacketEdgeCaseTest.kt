package com.unblocker.app.dns

import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DnsPacketEdgeCaseTest {

    @Test
    fun testParserRejectsIllegalDnsNameCharacters() {
        val packet = createDnsQueryPacket("ads/example.com")

        assertNull(DnsPacketUtil.parseIpPacket(packet, packet.size))
    }

    @Test
    fun testIhlLessThan20IsRejected() {
        // Version 4, IHL = 4 (16 bytes, illegal IPv4 header since min IHL is 5 = 20 bytes)
        val packet = ByteArray(60)
        packet[0] = 0x44.toByte() // Version 4, IHL 4 (16 bytes)
        packet[9] = 17.toByte() // UDP protocol
        packet[18] = 0.toByte()
        packet[19] = 53.toByte() // dstPort = 53 at udpOffset + 2
        packet[20] = 0.toByte()
        packet[21] = 30.toByte() // udpLength = 30
        packet[28] = 0.toByte()
        packet[29] = 1.toByte() // QDCOUNT = 1
        packet[36] = 3.toByte()
        packet[37] = 'c'.code.toByte()
        packet[38] = 'o'.code.toByte()
        packet[39] = 'm'.code.toByte()
        packet[40] = 0.toByte()
        packet[41] = 0.toByte()
        packet[42] = 1.toByte()
        packet[43] = 0.toByte()
        packet[44] = 1.toByte()

        val query = DnsPacketUtil.parseIpPacket(packet, packet.size)
        // Strictly must be rejected (null) due to RFC 791 IHL < 20 check
        assertNull("RFC 791 requires min IHL of 20, non-compliant packet must be rejected", query)
    }

    @Test
    fun testNegativeArraySizeVulnerabilityResolved() {
        // Construct a query where dnsLength is less than 12
        val corruptPacket = ByteArray(32)
        val corruptQuery = DnsQuery(
            transactionId = 1,
            domain = "example.com",
            queryType = 1,
            queryClass = 1,
            rawPacket = corruptPacket,
            dnsOffset = 0,
            dnsLength = 8, // dnsLength < 12
            srcIp = ByteArray(4),
            dstIp = ByteArray(4),
            srcPort = 12345,
            dstPort = 53
        )
        // Must not throw NegativeArraySizeException
        val resp = DnsPacketUtil.buildBlockedDnsResponsePacket(corruptQuery)
        assertNotNull("Response should be safely constructed without throwing", resp)
        assertTrue("Response packet must be at least 28 bytes", resp.size >= 28)
    }

    @Test
    fun testFuzzingDnsPacketParser50kIterations() {
        val random = Random(42)
        var crashes = 0
        for (i in 0 until 50_000) {
            val size = random.nextInt(1500) + 1
            val bytes = ByteArray(size)
            random.nextBytes(bytes)
            try {
                val q = DnsPacketUtil.parseIpPacket(bytes, size)
                if (q != null) {
                    DnsPacketUtil.buildBlockedDnsResponsePacket(q)
                }
            } catch (e: Throwable) {
                crashes++
                System.err.println("Crash on iteration $i with size $size: ${e.javaClass.name} - ${e.message}")
            }
        }
        assertEquals("Fuzzing must have 0 crashes across 50,000 malformed packets", 0, crashes)
    }

    @Test
    fun testPunycodeEntropyFalsePositiveBypass() {
        val learner = LocalNetworkLearner()
        // Punycode for Chinese / Russian / Arabic domain
        val idnDomain = "xn--fiqs8s.cn" // 中国.cn
        val sig = learner.extractDomainSignature(idnDomain)
        assertEquals("Punycode domain entropy must be bypassed (0.0)", 0.0f, sig.entropy, 0.001f)
        assertFalse(
            "Punycode domain must not be flagged as DGA algorithmic domain",
            sig.suspiciousPatterns.contains("High entropy DGA/algorithmic subdomain")
        )
    }

    @Test
    fun testConcurrentLearnerLoadUnderExtremeThroughput() {
        val learner = LocalNetworkLearner()
        val executor = Executors.newFixedThreadPool(8)
        val errorCount = java.util.concurrent.atomic.AtomicInteger(0)

        for (i in 0 until 1000) {
            executor.submit {
                try {
                    val domain = "analytics.tracker-$i.adnetwork.test"
                    val res = learner.analyzeQuery(domain)
                    assertTrue(res.score in 0.0f..1.0f)
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue("Executor should terminate within 10 seconds", finished)
        assertEquals("No errors during concurrent packet analysis", 0, errorCount.get())
    }

    private fun createDnsQueryPacket(domain: String): ByteArray {
        val labels = domain.split('.')
        val qnameLength = labels.sumOf { 1 + it.length } + 1
        val dnsLength = 12 + qnameLength + 4
        val udpLength = 8 + dnsLength
        val totalLength = 20 + udpLength
        return ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN).apply {
            put(0x45.toByte())
            put(0)
            putShort(totalLength.toShort())
            putShort(1)
            putShort(0)
            put(64)
            put(17)
            putShort(0)
            put(byteArrayOf(10, 10, 0, 2))
            put(byteArrayOf(10, 10, 0, 1))
            putShort(45_678.toShort())
            putShort(53)
            putShort(udpLength.toShort())
            putShort(0)
            putShort(0x1234)
            putShort(0x0100)
            putShort(1)
            putShort(0)
            putShort(0)
            putShort(0)
            labels.forEach { label ->
                put(label.length.toByte())
                put(label.toByteArray(Charsets.US_ASCII))
            }
            put(0)
            putShort(1)
            putShort(1)
        }.array()
    }
}
