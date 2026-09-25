package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsPacketTest {

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
    }

    private fun createMockDnsQueryPacket(domain: String, txId: Short): ByteArray {
        val labels = domain.split(".")
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
}
