package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsServfailResponseTest {

    private fun buildSyntheticQueryPacket(txId: Short, domain: String): ByteArray {
        val domainParts = domain.split(".")
        val qnameBytes = ByteBuffer.allocate(domain.length + 2)
        for (part in domainParts) {
            qnameBytes.put(part.length.toByte())
            qnameBytes.put(part.toByteArray(Charsets.US_ASCII))
        }
        qnameBytes.put(0.toByte())
        val questionSize = qnameBytes.position() + 4 // QTYPE (2) + QCLASS (2)

        val dnsSize = 12 + questionSize
        val udpLength = 8 + dnsSize
        val totalLength = 20 + udpLength

        val buf = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)
        // IPv4 Header (20 bytes)
        buf.put(0x45.toByte())
        buf.put(0.toByte())
        buf.putShort(totalLength.toShort())
        buf.putShort(0x1234.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0.toShort()) // Checksum
        buf.put(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 10.toByte())) // Src
        buf.put(byteArrayOf(10.toByte(), 10.toByte(), 0.toByte(), 1.toByte())) // Dst

        // UDP Header (8 bytes)
        buf.putShort(12345.toShort()) // srcPort
        buf.putShort(53.toShort()) // dstPort
        buf.putShort(udpLength.toShort())
        buf.putShort(0.toShort())

        // DNS Header (12 bytes)
        buf.putShort(txId)
        buf.putShort(0x0100.toShort()) // Standard query, RD=1
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Question
        buf.put(qnameBytes.array(), 0, qnameBytes.position())
        buf.putShort(1.toShort()) // QTYPE: A
        buf.putShort(1.toShort()) // QCLASS: IN

        return buf.array()
    }

    @Test
    fun testBuildServfailDnsResponsePacketPreservesTxIdAndQuestion() {
        val txId: Short = 0x7A9B.toShort()
        val queryPacket = buildSyntheticQueryPacket(txId, "example.com")
        val query = DnsPacketUtil.parseIpPacket(queryPacket, queryPacket.size)
        assertNotNull("Query should parse successfully", query)

        val servfailPacket = DnsPacketUtil.buildServfailDnsResponsePacket(query!!)
        assertNotNull("SERVFAIL packet should not be null", servfailPacket)

        // Verify IPv4 addresses swapped
        assertEquals(10.toByte(), servfailPacket[12]) // New src is old dst (10.10.0.1)
        assertEquals(192.toByte(), servfailPacket[16]) // New dst is old src (192.168.1.10)

        // Verify UDP ports swapped
        val respUdp = ByteBuffer.wrap(servfailPacket, 20, 8).order(ByteOrder.BIG_ENDIAN)
        assertEquals(53.toShort(), respUdp.short) // New src port = 53
        assertEquals(12345.toShort(), respUdp.short) // New dst port = 12345

        // Verify DNS header
        val respDns = ByteBuffer.wrap(servfailPacket, 28, 12).order(ByteOrder.BIG_ENDIAN)
        val echoedTxId = respDns.short
        val flags = respDns.short.toInt() and 0xFFFF
        val qdCount = respDns.short.toInt() and 0xFFFF
        val anCount = respDns.short.toInt() and 0xFFFF

        assertEquals("Echoed TxID must match query", txId, echoedTxId)
        assertEquals("QDCOUNT must be 1", 1, qdCount)
        assertEquals("ANCOUNT must be 0", 0, anCount)

        // Verify flags: QR=1, RCODE=2 (SERVFAIL)
        val isResponse = (flags and 0x8000) != 0
        val rcode = flags and 0x000F
        assertEquals("Must be response (QR=1)", true, isResponse)
        assertEquals("RCODE must be 2 (SERVFAIL)", 2, rcode)
    }

    @Test
    fun testBuildServfailWireResponseValidQuery() {
        val queryPacket = buildSyntheticQueryPacket(0x3344.toShort(), "sub.domain.test")
        val dnsWireBytes = queryPacket.copyOfRange(28, queryPacket.size)

        val servfailWire = DnsPacketUtil.buildServfailWireResponse(dnsWireBytes)
        assertNotNull("Wire response must not be null", servfailWire)

        val respBuf = ByteBuffer.wrap(servfailWire!!).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0x3344.toShort(), respBuf.short)
        val flags = respBuf.short.toInt() and 0xFFFF
        assertEquals("RCODE must be 2 (SERVFAIL)", 2, flags and 0x000F)
        assertEquals("QDCOUNT must be 1", 1, respBuf.short.toInt() and 0xFFFF)
        assertEquals("ANCOUNT must be 0", 0, respBuf.short.toInt() and 0xFFFF)
    }

    @Test
    fun testBuildServfailWireResponseMalformedPayloadReturnsNull() {
        val garbage = byteArrayOf(1, 2, 3, 4)
        assertNull(DnsPacketUtil.buildServfailWireResponse(garbage))

        val badQdCount = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .putShort(1)
            .putShort(0)
            .putShort(2.toShort()) // qdCount != 1
            .array()
        assertNull(DnsPacketUtil.buildServfailWireResponse(badQdCount))
    }
}
