package com.unblocker.vpn.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [DnsPacketEngine].
 *
 * Verifies IPv4/IPv6 packet parsing, 16-bit one's complement checksum validation (0xFFFF verification),
 * and RFC-compliant NXDOMAIN (0x8183) packet synthesis.
 */
class DnsPacketEngineTest {

    private fun buildDnsQueryPayload(domain: String, txId: Short = 0x1234.toShort()): ByteArray {
        val out = ByteArrayOutputStream()
        // DNS Header (12 bytes)
        out.write((txId.toInt() ushr 8) and 0xFF)
        out.write(txId.toInt() and 0xFF)
        out.write(0x01) // Flags: RD=1
        out.write(0x00)
        out.write(0x00) // QDCOUNT = 1
        out.write(0x01)
        out.write(0x00) // ANCOUNT = 0
        out.write(0x00)
        out.write(0x00) // NSCOUNT = 0
        out.write(0x00)
        out.write(0x00) // ARCOUNT = 0
        out.write(0x00)

        // QNAME wire labels
        val parts = domain.split('.')
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00) // Root label null terminator

        // QTYPE = 1 (A), QCLASS = 1 (IN)
        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x01)

        return out.toByteArray()
    }

    private fun buildSyntheticIpv4Packet(
        domain: String,
        srcPort: Int = 54321,
        dstPort: Int = 53,
        txId: Short = 0x1234.toShort()
    ): ByteArray {
        val dnsPayload = buildDnsQueryPayload(domain, txId)
        val udpLen = 8 + dnsPayload.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)

        // IPv4 Header (20 bytes)
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0x00
        packet[2] = ((totalLen ushr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0x00; packet[5] = 0x01 // Identification
        packet[6] = 0x40.toByte(); packet[7] = 0x00 // DF flag
        packet[8] = 64 // TTL
        packet[9] = 17 // UDP protocol
        packet[10] = 0; packet[11] = 0 // Checksum placeholder

        // Src IP: 192.168.1.100
        packet[12] = 192.toByte(); packet[13] = 168.toByte(); packet[14] = 1; packet[15] = 100
        // Dst IP: 10.111.222.2
        packet[16] = 10; packet[17] = 111.toByte(); packet[18] = 222.toByte(); packet[19] = 2

        // Compute valid IPv4 header checksum
        val cksum = DnsPacketEngine.calculateIpv4Checksum(packet, 0, 20)
        packet[10] = ((cksum.toInt() ushr 8) and 0xFF).toByte()
        packet[11] = (cksum.toInt() and 0xFF).toByte()

        // UDP Header (8 bytes)
        packet[20] = ((srcPort ushr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort ushr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        packet[26] = 0; packet[27] = 0

        // Copy DNS Payload
        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)

        return packet
    }

    private fun buildSyntheticIpv6Packet(
        domain: String,
        srcPort: Int = 45678,
        dstPort: Int = 53,
        txId: Short = 0x5678.toShort()
    ): ByteArray {
        val dnsPayload = buildDnsQueryPayload(domain, txId)
        val udpLen = 8 + dnsPayload.size
        val totalLen = 40 + udpLen
        val packet = ByteArray(totalLen)

        // IPv6 Header (40 bytes)
        packet[0] = 0x60.toByte() // Version 6
        packet[1] = 0x00; packet[2] = 0x00; packet[3] = 0x00
        packet[4] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[5] = (udpLen and 0xFF).toByte()
        packet[6] = 17 // Next Header = UDP
        packet[7] = 64 // Hop Limit

        // Src IPv6: 2001:db8::1 (bytes 8..23)
        packet[8] = 0x20; packet[9] = 0x01; packet[10] = 0x0d; packet[11] = 0xb8.toByte()
        packet[23] = 0x01

        // Dst IPv6: fd00:1:fd00:1:fd00:1:fd00:2 (bytes 24..39)
        packet[24] = 0xfd.toByte(); packet[25] = 0x00; packet[26] = 0x00; packet[27] = 0x01
        packet[28] = 0xfd.toByte(); packet[29] = 0x00; packet[30] = 0x00; packet[31] = 0x01
        packet[32] = 0xfd.toByte(); packet[33] = 0x00; packet[34] = 0x00; packet[35] = 0x01
        packet[36] = 0xfd.toByte(); packet[37] = 0x00; packet[38] = 0x00; packet[39] = 0x02

        // UDP Header (8 bytes) at offset 40
        packet[40] = ((srcPort ushr 8) and 0xFF).toByte()
        packet[41] = (srcPort and 0xFF).toByte()
        packet[42] = ((dstPort ushr 8) and 0xFF).toByte()
        packet[43] = (dstPort and 0xFF).toByte()
        packet[44] = ((udpLen ushr 8) and 0xFF).toByte()
        packet[45] = (udpLen and 0xFF).toByte()
        packet[46] = 0; packet[47] = 0

        // Copy DNS Payload
        System.arraycopy(dnsPayload, 0, packet, 48, dnsPayload.size)

        // Calculate valid IPv6 UDP checksum
        val udpCksum = DnsPacketEngine.calculateIpv6UdpChecksum(packet, 40, udpLen)
        packet[46] = ((udpCksum.toInt() ushr 8) and 0xFF).toByte()
        packet[47] = (udpCksum.toInt() and 0xFF).toByte()

        return packet
    }

    @Test
    fun testParseIpv4PacketAndSynthesizeNxDomain() {
        val domain = "googleads.g.doubleclick.net"
        val rawPacket = buildSyntheticIpv4Packet(domain, srcPort = 54321, dstPort = 53, txId = 0x1234.toShort())

        // 1. Verify parsing
        val query = DnsPacketEngine.parseOutgoingDnsPacket(rawPacket, rawPacket.size)
        assertNotNull("Outgoing DNS query must parse successfully", query)
        assertEquals("googleads.g.doubleclick.net", query!!.domain)
        assertFalse("Must be IPv4", query.isIpv6)
        assertEquals(20, query.ipHeaderLength)
        assertEquals(54321, query.srcPort)
        assertEquals(53, query.dstPort)
        assertEquals(0x1234.toShort(), query.txId)

        // 2. Synthesize NXDOMAIN response
        val response = DnsPacketEngine.synthesizeNxDomainResponse(rawPacket, query)
        assertEquals("Response length must equal original request length", rawPacket.size, response.size)

        // 3. Verify IPv4 Header Checksum (must sum to 0xFFFF)
        assertTrue(
            "IPv4 header checksum must verify as 0xFFFF",
            DnsPacketEngine.verifyIpv4Checksum(response, 0, 20)
        )

        // 4. Verify Swapped Source and Destination IPs
        assertEquals(10, response[12].toInt())
        assertEquals(111, response[13].toInt() and 0xFF)
        assertEquals(222, response[14].toInt() and 0xFF)
        assertEquals(2, response[15].toInt())

        assertEquals(192, response[16].toInt() and 0xFF)
        assertEquals(168, response[17].toInt() and 0xFF)
        assertEquals(1, response[18].toInt())
        assertEquals(100, response[19].toInt())

        // 5. Verify Swapped UDP Ports (src=53, dst=54321)
        val respSrcPort = ((response[20].toInt() and 0xFF) shl 8) or (response[21].toInt() and 0xFF)
        val respDstPort = ((response[22].toInt() and 0xFF) shl 8) or (response[23].toInt() and 0xFF)
        assertEquals(53, respSrcPort)
        assertEquals(54321, respDstPort)

        // 6. Verify DNS Flags: 0x8183 (QR=1, RD=1, RA=1, RCODE=3 NXDOMAIN)
        val dnsOffset = query.dnsOffset
        val flags = ((response[dnsOffset + 2].toInt() and 0xFF) shl 8) or (response[dnsOffset + 3].toInt() and 0xFF)
        assertEquals("DNS flags must be 0x8183 (NXDOMAIN)", 0x8183, flags)

        // 7. Verify counts: ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        val anCount = ((response[dnsOffset + 6].toInt() and 0xFF) shl 8) or (response[dnsOffset + 7].toInt() and 0xFF)
        val nsCount = ((response[dnsOffset + 8].toInt() and 0xFF) shl 8) or (response[dnsOffset + 9].toInt() and 0xFF)
        val arCount = ((response[dnsOffset + 10].toInt() and 0xFF) shl 8) or (response[dnsOffset + 11].toInt() and 0xFF)
        assertEquals(0, anCount)
        assertEquals(0, nsCount)
        assertEquals(0, arCount)
    }

    @Test
    fun testParseIpv6PacketAndSynthesizeNxDomain() {
        val domain = "pagead2.googlesyndication.com"
        val rawPacket = buildSyntheticIpv6Packet(domain, srcPort = 45678, dstPort = 53, txId = 0x5678.toShort())

        // 1. Verify parsing
        val query = DnsPacketEngine.parseOutgoingDnsPacket(rawPacket, rawPacket.size)
        assertNotNull("IPv6 outgoing DNS query must parse successfully", query)
        assertEquals("pagead2.googlesyndication.com", query!!.domain)
        assertTrue("Must be IPv6", query.isIpv6)
        assertEquals(40, query.ipHeaderLength)
        assertEquals(45678, query.srcPort)
        assertEquals(53, query.dstPort)
        assertEquals(0x5678.toShort(), query.txId)

        // 2. Synthesize NXDOMAIN response
        val response = DnsPacketEngine.synthesizeNxDomainResponse(rawPacket, query)
        assertEquals(rawPacket.size, response.size)

        // 3. Verify swapped IPv6 addresses (src bytes 8..23 now contain fd00:1...)
        assertEquals(0xfd.toByte(), response[8])
        assertEquals(0x00.toByte(), response[9])
        assertEquals(0x02.toByte(), response[23])

        // 4. Verify swapped UDP ports (src=53, dst=45678)
        val u = query.udpOffset
        val respSrcPort = ((response[u].toInt() and 0xFF) shl 8) or (response[u + 1].toInt() and 0xFF)
        val respDstPort = ((response[u + 2].toInt() and 0xFF) shl 8) or (response[u + 3].toInt() and 0xFF)
        assertEquals(53, respSrcPort)
        assertEquals(45678, respDstPort)

        // 5. Verify DNS flags: 0x8183
        val d = query.dnsOffset
        val flags = ((response[d + 2].toInt() and 0xFF) shl 8) or (response[d + 3].toInt() and 0xFF)
        assertEquals(0x8183, flags)

        // 6. Verify non-zero IPv6 UDP checksum
        val udpCksum = ((response[u + 6].toInt() and 0xFF) shl 8) or (response[u + 7].toInt() and 0xFF)
        assertTrue("IPv6 UDP checksum must not be 0", udpCksum != 0)
    }

    @Test
    fun testNonDnsPacketRejected() {
        val rawPacket = buildSyntheticIpv4Packet("example.com", dstPort = 80) // Port 80, not DNS 53
        val query = DnsPacketEngine.parseOutgoingDnsPacket(rawPacket, rawPacket.size)
        assertNull("Non-DNS packet must return null", query)
    }
}
