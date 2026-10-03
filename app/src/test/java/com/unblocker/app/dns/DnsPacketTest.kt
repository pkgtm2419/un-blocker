package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketTest {

    private fun ipv4Query(): DnsQuery {
        val packet = createMockDnsQueryPacket("ads.example", 0x1234)
        return DnsPacketUtil.parseIpPacket(packet, packet.size)!!
    }

    @Test fun ipv4WrapperEnforcesMaximumRepresentableDnsPayload() {
        val query = ipv4Query()

        val maximum = DnsPacketUtil.maxDnsPayloadLength(query)
        val packet = DnsPacketUtil.wrapDnsResponseInIpUdp(query, ByteArray(maximum), maximum)

        assertEquals(65_507, maximum)
        assertEquals(65_535, packet.size)
        assertEquals(0xffff, ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff))
        assertThrows(IllegalArgumentException::class.java) {
            DnsPacketUtil.wrapDnsResponseInIpUdp(query, ByteArray(maximum + 1), maximum + 1)
        }
    }

    @Test
    fun testParseAndBuildBlockedDnsResponse() {
        // Construct a synthetic DNS query packet for "ads.google.com"
        val domain = "ads.google.com"
        val txId: Short = 0x1234
        val rawIpPacket = createMockDnsQueryPacket(domain, txId)

        // 1. Parse packet
        val query = DnsPacketUtil.parseIpPacket(rawIpPacket, rawIpPacket.size)
        assertNotNull("Query should not be null", query)
        assertEquals("Domain should match", domain, query!!.domain)
        assertEquals("Transaction ID should match", txId, query.transactionId)
        assertEquals("Query type should be A (1)", DnsPacketUtil.TYPE_A, query.queryType)

        // 2. Synthesize 0.0.0.0 response
        val response = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
        assertNotNull("Response packet should not be null", response)
        assertTrue("Response packet should be >= 28 bytes", response.size >= 28)

        // Verify IP Header in response
        assertEquals(0x45.toByte(), response[0]) // IPv4, IHL 5
        assertEquals(17.toByte(), response[9]) // Protocol UDP

        // Verify Source Port is 53
        val srcPort = ((response[20].toInt() and 0xFF) shl 8) or (response[21].toInt() and 0xFF)
        assertEquals(53, srcPort)

        // Verify Transaction ID in DNS response
        val dnsTxId = ((response[28].toInt() and 0xFF) shl 8) or (response[29].toInt() and 0xFF)
        assertEquals(txId.toInt() and 0xFFFF, dnsTxId)

        // Verify Answer contains 0.0.0.0 at the end of the packet
        val end = response.size
        assertEquals(0.toByte(), response[end - 4])
        assertEquals(0.toByte(), response[end - 3])
        assertEquals(0.toByte(), response[end - 2])
        assertEquals(0.toByte(), response[end - 1])
        assertEquals(300L, DnsPacketUtil.minCacheTtlSeconds(response.copyOfRange(28, response.size)))
    }

    @Test fun cacheTtlRejectsQueriesAndMalformedResponses() {
        val query = createMockDnsQueryPacket("example.com", 0x4321)
        assertEquals(null, DnsPacketUtil.minCacheTtlSeconds(query))
        assertEquals(null, DnsPacketUtil.minCacheTtlSeconds(byteArrayOf(1, 2, 3)))
    }

    @Test fun queryParserRejectsSingleWireLabelContainingDot() {
        val packet = createMockDnsQueryPacketFromLabels(listOf("ads.example"), 0x1234)

        assertEquals(null, DnsPacketUtil.parseIpPacket(packet, packet.size))
    }

    @Test fun queryParserRejectsWireLabelThatWouldOnlyMatchAfterTrimming() {
        val packet = createMockDnsQueryPacketFromLabels(listOf(" ads", "example"), 0x1234)

        assertEquals(null, DnsPacketUtil.parseIpPacket(packet, packet.size))
    }

    private fun createMockDnsQueryPacket(domain: String, txId: Short): ByteArray {
        return createMockDnsQueryPacketFromLabels(domain.split("."), txId)
    }

    private fun createMockDnsQueryPacketFromLabels(labels: List<String>, txId: Short): ByteArray {
        var qnameLen = 1 // trailing 0
        for (l in labels) {
            qnameLen += 1 + l.length
        }

        val dnsLen = 12 + qnameLen + 4 // Header (12) + QNAME + QTYPE(2) + QCLASS(2)
        val udpLen = 8 + dnsLen
        val ipTotalLen = 20 + udpLen

        val buf = ByteBuffer.allocate(ipTotalLen)
        buf.order(ByteOrder.BIG_ENDIAN)

        // IP Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(ipTotalLen.toShort())
        buf.putShort(0x0001.toShort())
        buf.putShort(0x0000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0x0000.toShort()) // checksum placeholder
        buf.put(byteArrayOf(10, 10, 0, 2)) // src IP
        buf.put(byteArrayOf(10, 10, 0, 1)) // dst IP

        // UDP Header
        buf.putShort(45678.toShort()) // src port
        buf.putShort(53.toShort()) // dst port
        buf.putShort(udpLen.toShort())
        buf.putShort(0x0000.toShort())

        // DNS Header
        buf.putShort(txId)
        buf.putShort(0x0100.toShort()) // Standard Query, Recursion Desired
        buf.putShort(1.toShort()) // QDCOUNT
        buf.putShort(0.toShort()) // ANCOUNT
        buf.putShort(0.toShort()) // NSCOUNT
        buf.putShort(0.toShort()) // ARCOUNT

        // QNAME
        for (l in labels) {
            buf.put(l.length.toByte())
            buf.put(l.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0x00.toByte())

        // QTYPE A & QCLASS IN
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        return buf.array()
    }

    @Test
    fun testParseAndBuildBlockedDnsResponseIpv6() {
        val domain = "adservice.google.com"
        val txId: Short = 0x5678
        val rawIpPacket = createMockIpv6DnsQueryPacket(domain, txId)

        // 1. Parse IPv6 packet
        val query = DnsPacketUtil.parseIpPacket(rawIpPacket, rawIpPacket.size)
        assertNotNull("IPv6 Query should not be null", query)
        assertTrue("Query should be marked as IPv6", query!!.isIpv6)
        assertEquals("Domain should match", domain, query.domain)
        assertEquals("Transaction ID should match", txId, query.transactionId)

        // 2. Synthesize IPv6 blocked response
        val response = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
        assertNotNull("IPv6 response should not be null", response)
        assertTrue("IPv6 packet should be >= 48 bytes", response.size >= 48)

        // Verify IPv6 Header (byte 0 should have version 6)
        assertEquals(0x60.toByte(), (response[0].toInt() and 0xF0).toByte())
        assertEquals(17.toByte(), response[6]) // Protocol UDP

        // Verify Source Port is 53 at offset 40
        val srcPort = ((response[40].toInt() and 0xFF) shl 8) or (response[41].toInt() and 0xFF)
        assertEquals(53, srcPort)

        // Verify Transaction ID in DNS response at offset 48
        val dnsTxId = ((response[48].toInt() and 0xFF) shl 8) or (response[49].toInt() and 0xFF)
        assertEquals(txId.toInt() and 0xFFFF, dnsTxId)
        assertValidIpv6UdpChecksum(response)
    }

    @Test fun blockedIpv6AaaaResponseHasValidUdpChecksum() {
        val packet = createMockIpv6DnsQueryPacket(
            domain = "ipv6-ads.example",
            txId = 0x5a5a,
            queryType = DnsPacketUtil.TYPE_AAAA
        )
        val query = DnsPacketUtil.parseIpPacket(packet, packet.size)!!

        val response = DnsPacketUtil.buildBlockedDnsResponsePacket(query)

        assertValidIpv6UdpChecksum(response)
    }

    @Test fun wrappedOddLengthIpv6ResponseHasValidUdpChecksum() {
        val packet = createMockIpv6DnsQueryPacket("allowed.example", 0x6b6b)
        val query = DnsPacketUtil.parseIpPacket(packet, packet.size)!!
        val dnsPayload = ByteArray(13) { (it + 1).toByte() }

        val response = DnsPacketUtil.wrapDnsResponseInIpUdp(query, dnsPayload, dnsPayload.size)

        assertEquals(dnsPayload.toList(), response.copyOfRange(48, response.size).toList())
        assertValidIpv6UdpChecksum(response)
    }

    @Test
    fun testDoHCanaryDomainReturnsNxDomain() {
        val canary = "use-application-dns.net"
        val txId: Short = 0x4ABC.toShort()
        val rawIpPacket = createMockDnsQueryPacket(canary, txId)

        val query = DnsPacketUtil.parseIpPacket(rawIpPacket, rawIpPacket.size)
        assertNotNull(query)

        val response = DnsPacketUtil.buildBlockedDnsResponsePacket(query!!)
        assertNotNull(response)

        // DNS Header starts at byte 28 for IPv4
        val flags = ((response[30].toInt() and 0xFF) shl 8) or (response[31].toInt() and 0xFF)
        val rcode = flags and 0x000F
        assertEquals("RCODE must be 3 (NXDOMAIN) for DoH canary domain", 3, rcode)

        val anCount = ((response[34].toInt() and 0xFF) shl 8) or (response[35].toInt() and 0xFF)
        assertEquals("ANCOUNT must be 0 for DoH canary NXDOMAIN", 0, anCount)
    }

    private fun createMockIpv6DnsQueryPacket(
        domain: String,
        txId: Short,
        queryType: Short = DnsPacketUtil.TYPE_A
    ): ByteArray {
        val labels = domain.split(".")
        var qnameLen = 1 // trailing 0
        for (l in labels) {
            qnameLen += 1 + l.length
        }

        val dnsLen = 12 + qnameLen + 4 // Header (12) + QNAME + QTYPE(2) + QCLASS(2)
        val udpLen = 8 + dnsLen
        val ipTotalLen = 40 + udpLen

        val buf = ByteBuffer.allocate(ipTotalLen)
        buf.order(ByteOrder.BIG_ENDIAN)

        // IPv6 Header (40 bytes)
        buf.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
        buf.putShort(udpLen.toShort()) // Payload length
        buf.put(17.toByte()) // Next Header: UDP
        buf.put(64.toByte()) // Hop Limit
        buf.put(ByteArray(16) { 0x01 }) // Src IP (fd00:1::2)
        buf.put(ByteArray(16) { 0x02 }) // Dst IP (fd00:1::1)

        // UDP Header (8 bytes)
        buf.putShort(54321.toShort()) // src port
        buf.putShort(53.toShort()) // dst port
        buf.putShort(udpLen.toShort())
        buf.putShort(0x0000.toShort())

        // DNS Header
        buf.putShort(txId)
        buf.putShort(0x0100.toShort()) // Standard Query, Recursion Desired
        buf.putShort(1.toShort()) // QDCOUNT
        buf.putShort(0.toShort()) // ANCOUNT
        buf.putShort(0.toShort()) // NSCOUNT
        buf.putShort(0.toShort()) // ARCOUNT

        // QNAME
        for (l in labels) {
            buf.put(l.length.toByte())
            buf.put(l.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0x00.toByte())

        buf.putShort(queryType)
        buf.putShort(DnsPacketUtil.CLASS_IN)

        return buf.array()
    }

    private fun assertValidIpv6UdpChecksum(packet: ByteArray) {
        val udpLength = unsignedShort(packet, 44)
        val checksum = unsignedShort(packet, 46)
        assertTrue("IPv6 UDP checksum must be non-zero", checksum != 0)

        var sum = 0L
        fun addWord(high: Int, low: Int) {
            sum += ((high and 0xff) shl 8) or (low and 0xff)
            while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        }
        for (offset in 8 until 40 step 2) addWord(packet[offset].toInt(), packet[offset + 1].toInt())
        addWord(0, 0)
        addWord((udpLength ushr 8) and 0xff, udpLength and 0xff)
        addWord(0, 17)
        for (offset in 40 until 40 + udpLength step 2) {
            addWord(packet[offset].toInt(), if (offset + 1 < 40 + udpLength) packet[offset + 1].toInt() else 0)
        }
        assertEquals("IPv6 pseudo-header plus UDP segment must sum to all ones", 0xffff, sum.toInt())
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
