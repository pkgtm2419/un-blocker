package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsErrorResponseTest {

    private fun buildSyntheticUdpPacket(txId: Short, flags: Short, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
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
        buf.put(byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 10.toByte())) // Src: 192.168.1.10
        buf.put(byteArrayOf(10.toByte(), 10.toByte(), 0.toByte(), 1.toByte())) // Dst: 10.10.0.1
        // UDP Header (8 bytes)
        buf.putShort(12345.toShort()) // srcPort
        buf.putShort(53.toShort()) // dstPort
        buf.putShort(udpLength.toShort())
        buf.putShort(0.toShort())
        // DNS Payload
        buf.put(payload)
        return buf.array()
    }

    @Test
    fun testIntactHeaderInvalidBodyReturnsFormerr() {
        // 12-byte DNS header with qdCount = 5 (invalid/unsupported), txId = 0x4321
        val dnsHeader = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putShort(0x4321.toShort())
            .putShort(0x0100.toShort()) // Query with RD=1, opcode=0
            .putShort(5.toShort()) // qdCount = 5
            .putShort(0)
            .putShort(0)
            .putShort(0)
            .array()

        val packet = buildSyntheticUdpPacket(0x4321.toShort(), 0x0100.toShort(), dnsHeader)
        val query = DnsPacketUtil.parseIpPacket(packet, packet.size)
        assertNull("Query should fail validation", query)

        val errResp = DnsPacketUtil.buildErrorResponseIfApplicable(packet, packet.size)
        assertNotNull("Should build error response for intact header", errResp)

        // Verify response DNS header
        val respDns = ByteBuffer.wrap(errResp!!, 28, 12).order(ByteOrder.BIG_ENDIAN)
        val echoedTxId = respDns.short
        val respFlags = respDns.short.toInt() and 0xFFFF
        assertEquals(0x4321.toShort(), echoedTxId)
        val rcode = respFlags and 0x000F
        assertEquals("FORMERR (RCODE=1) expected", 1, rcode)
    }

    @Test
    fun testNonZeroOpcodeReturnsNotImp() {
        // DNS header with opcode = 2 (STATUS), txId = 0x5555
        val flags = (2 shl 11).toShort()
        val dnsHeader = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putShort(0x5555.toShort())
            .putShort(flags)
            .putShort(1)
            .putShort(0)
            .putShort(0)
            .putShort(0)
            .array()

        val packet = buildSyntheticUdpPacket(0x5555.toShort(), flags, dnsHeader)
        val errResp = DnsPacketUtil.buildErrorResponseIfApplicable(packet, packet.size)
        assertNotNull("Should build error response for non-QUERY opcode", errResp)

        val respDns = ByteBuffer.wrap(errResp!!, 28, 12).order(ByteOrder.BIG_ENDIAN)
        val echoedTxId = respDns.short
        val respFlags = respDns.short.toInt() and 0xFFFF
        assertEquals(0x5555.toShort(), echoedTxId)
        val rcode = respFlags and 0x000F
        assertEquals("NOTIMP (RCODE=4) expected for non-zero opcode", 4, rcode)
    }

    @Test
    fun testGarbageUnder12BytesSilentlyDropped() {
        val garbagePayload = byteArrayOf(1, 2, 3, 4, 5) // Only 5 bytes
        val packet = buildSyntheticUdpPacket(1, 0, garbagePayload)
        val errResp = DnsPacketUtil.buildErrorResponseIfApplicable(packet, packet.size)
        assertNull("Garbage under 12 bytes must be dropped silently", errResp)
    }
}
