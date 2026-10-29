package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.toForwardingRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingDnsRequestTest {

    private fun buildSyntheticQueryBuffer(txId: Short, domain: String): ByteArray {
        val domainParts = domain.split(".")
        val qnameBytes = ByteBuffer.allocate(domain.length + 2)
        for (part in domainParts) {
            qnameBytes.put(part.length.toByte())
            qnameBytes.put(part.toByteArray(Charsets.US_ASCII))
        }
        qnameBytes.put(0.toByte())
        val questionSize = qnameBytes.position() + 4

        val dnsSize = 12 + questionSize
        val udpLength = 8 + dnsSize
        val totalLength = 20 + udpLength

        // Allocate larger buffer to simulate 4096-byte pooled packet buffer
        val buffer = ByteArray(4096)
        val buf = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header (20 bytes)
        buf.put(0x45.toByte())
        buf.put(0.toByte())
        buf.putShort(totalLength.toShort())
        buf.putShort(0x1234.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0.toShort())
        buf.put(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 10.toByte()))
        buf.put(byteArrayOf(10.toByte(), 10.toByte(), 0.toByte(), 1.toByte()))

        // UDP Header (8 bytes)
        buf.putShort(12345.toShort())
        buf.putShort(53.toShort())
        buf.putShort(udpLength.toShort())
        buf.putShort(0.toShort())

        // DNS Header (12 bytes)
        buf.putShort(txId)
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        // Question
        buf.put(qnameBytes.array(), 0, qnameBytes.position())
        buf.putShort(1.toShort()) // A
        buf.putShort(1.toShort()) // IN

        return buffer
    }

    @Test
    fun testParseDirectlyInPooledBufferWithoutFullCopy() {
        val buffer = buildSyntheticQueryBuffer(0x4567.toShort(), "isolated.test.com")
        val length = 20 + 8 + 12 + ("isolated.test.com".length + 2) + 4

        val query = DnsPacketUtil.parseIpPacket(buffer, length)
        assertNotNull("Query should parse successfully from pooled buffer", query)
        assertEquals("isolated.test.com", query!!.domain)
        assertEquals(0x4567.toShort(), query.transactionId)
        assertTrue("rawPacket in query points directly to buffer", query.rawPacket === buffer)
    }

    @Test
    fun testToForwardingRequestIsolatesFromSubsequentBufferMutation() {
        val buffer = buildSyntheticQueryBuffer(0x1234.toShort(), "immutable.example.org")
        val length = 20 + 8 + 12 + ("immutable.example.org".length + 2) + 4

        val query = DnsPacketUtil.parseIpPacket(buffer, length)
        assertNotNull(query)

        val forwardingRequest = query!!.toForwardingRequest()
        val originalDnsPayload = forwardingRequest.dnsPayload.copyOf()

        // Mutate original pooled buffer as if next TUN read occurred
        buffer.fill(0xFF.toByte(), 0, length)

        // Verify forwarding request is completely decoupled and intact
        assertEquals("immutable.example.org", forwardingRequest.query.domain)
        assertEquals(0x1234.toShort(), forwardingRequest.query.transactionId)
        assertArrayEquals(originalDnsPayload, forwardingRequest.dnsPayload)
        // Verify rawPacket in forwarding query was decoupled from pooled buffer
        assertTrue(forwardingRequest.query.rawPacket !== buffer)
    }
}
