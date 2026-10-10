package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.InternetChecksum
import com.unblocker.app.logic.dns.TcpDnsResponder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TcpDnsResponderTest {
    private val emittedPackets = mutableListOf<ByteArray>()
    private val immediateExecutor = java.util.concurrent.Executor { it.run() }
    
    private fun createResponder(
        queryResolver: (ByteArray, Boolean, ByteArray) -> ByteArray? = { _, _, _ -> null },
        maxConnections: Int = 32,
        maxBufferSize: Int = 65536,
        idleTimeoutMillis: Long = 5000L
    ): TcpDnsResponder {
        return TcpDnsResponder(
            executor = immediateExecutor,
            emit = { emittedPackets.addAll(it) },
            queryResolver = queryResolver,
            maxConnections = maxConnections,
            maxBufferSize = maxBufferSize,
            idleTimeoutMillis = idleTimeoutMillis
        )
    }
    
    private fun process(responder: TcpDnsResponder, packet: ByteArray): List<ByteArray> {
        emittedPackets.clear()
        responder.processPacket(packet, packet.size)
        return emittedPackets.toList()
    }


    private val ipv4Dns = byteArrayOf(10, 10, 0, 1)
    private val ipv4Client = byteArrayOf(10, 10, 0, 2)
    private val ipv6Dns = byteArrayOf(
        0xfd.toByte(), 0x00, 0x00, 0x01,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 1
    )
    private val ipv6Client = byteArrayOf(
        0xfd.toByte(), 0x00, 0x00, 0x01,
        0, 0, 0, 0,
        0, 0, 0, 0,
        0, 0, 0, 2
    )

    private fun buildClientTcpPacket(
        isIpv6: Boolean = false,
        srcIp: ByteArray = if (isIpv6) ipv6Client else ipv4Client,
        dstIp: ByteArray = if (isIpv6) ipv6Dns else ipv4Dns,
        srcPort: Int = 45678,
        dstPort: Int = 53,
        seqNum: Long = 1000L,
        ackNum: Long = 0L,
        flags: Int = TcpDnsResponder.FLAG_SYN,
        payload: ByteArray? = null
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val tcpSegmentLen = 20 + payloadLen
        val ipHeaderLen = if (isIpv6) 40 else 20
        val totalLen = ipHeaderLen + tcpSegmentLen

        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)
        if (isIpv6) {
            buf.putInt(0x60000000)
            buf.putShort(tcpSegmentLen.toShort())
            buf.put(6.toByte())
            buf.put(64.toByte())
            buf.put(srcIp)
            buf.put(dstIp)
        } else {
            buf.put(0x45.toByte())
            buf.put(0.toByte())
            buf.putShort(totalLen.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0x4000.toShort())
            buf.put(64.toByte())
            buf.put(6.toByte())
            buf.putShort(0.toShort())
            buf.put(srcIp)
            buf.put(dstIp)
            buf.putShort(10, InternetChecksum.ipv4Header(buf.array(), 0, 20))
        }

        val tcpOffset = ipHeaderLen
        buf.position(tcpOffset)
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putInt(seqNum.toInt())
        buf.putInt(ackNum.toInt())
        buf.put(0x50.toByte())
        buf.put(flags.toByte())
        buf.putShort(65535.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        if (payload != null) {
            buf.put(payload)
        }

        val checksum = if (isIpv6) {
            InternetChecksum.tcpIpv6(srcIp, dstIp, buf.array(), tcpOffset, tcpSegmentLen)
        } else {
            InternetChecksum.tcpIpv4(srcIp, dstIp, buf.array(), tcpOffset, tcpSegmentLen)
        }
        buf.putShort(tcpOffset + 16, checksum)

        return buf.array()
    }

    private data class ParsedTcpPacket(
        val isIpv6: Boolean,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val seqNum: Long,
        val ackNum: Long,
        val flags: Int,
        val payload: ByteArray
    )

    private fun parseTcpPacket(packet: ByteArray): ParsedTcpPacket {
        val isIpv6 = (packet[0].toInt() and 0xF0) == 0x60
        val (ipHeaderLen, srcIp, dstIp) = if (isIpv6) {
            Triple(40, packet.copyOfRange(8, 24), packet.copyOfRange(24, 40))
        } else {
            Triple((packet[0].toInt() and 0x0F) * 4, packet.copyOfRange(12, 16), packet.copyOfRange(16, 20))
        }

        val tcpOffset = ipHeaderLen
        val srcPort = ((packet[tcpOffset].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[tcpOffset + 2].toInt() and 0xFF) shl 8) or (packet[tcpOffset + 3].toInt() and 0xFF)
        val seqNum = ((packet[tcpOffset + 4].toLong() and 0xFF) shl 24) or
            ((packet[tcpOffset + 5].toLong() and 0xFF) shl 16) or
            ((packet[tcpOffset + 6].toLong() and 0xFF) shl 8) or
            (packet[tcpOffset + 7].toLong() and 0xFF)
        val ackNum = ((packet[tcpOffset + 8].toLong() and 0xFF) shl 24) or
            ((packet[tcpOffset + 9].toLong() and 0xFF) shl 16) or
            ((packet[tcpOffset + 10].toLong() and 0xFF) shl 8) or
            (packet[tcpOffset + 11].toLong() and 0xFF)
        val dataOffset = (packet[tcpOffset + 12].toInt() and 0xF0) shr 4
        val tcpHeaderLen = dataOffset * 4
        val flags = packet[tcpOffset + 13].toInt() and 0xFF
        val payloadOffset = tcpOffset + tcpHeaderLen
        val payloadLen = packet.size - payloadOffset
        val payload = if (payloadLen > 0) packet.copyOfRange(payloadOffset, packet.size) else ByteArray(0)

        // Verify TCP checksum
        val tcpSegmentLen = packet.size - tcpOffset
        val verify = if (isIpv6) {
            InternetChecksum.tcpIpv6(srcIp, dstIp, packet, tcpOffset, tcpSegmentLen)
        } else {
            InternetChecksum.tcpIpv4(srcIp, dstIp, packet, tcpOffset, tcpSegmentLen)
        }
        assertEquals("TCP checksum verification failed", 0xffff, verify.toInt() and 0xffff)

        return ParsedTcpPacket(isIpv6, srcIp, dstIp, srcPort, dstPort, seqNum, ackNum, flags, payload)
    }

    private fun buildSyntheticDnsQuery(name: String = "example.com", txId: Short = 0x1234.toShort(), type: Short = 1): ByteArray {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(txId)
        buf.putShort(0x0100.toShort()) // RD = 1
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        for (label in name.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            buf.put(bytes.size.toByte())
            buf.put(bytes)
        }
        buf.put(0.toByte())
        buf.putShort(type) // QTYPE
        buf.putShort(1.toShort()) // QCLASS = IN

        val size = buf.position()
        return buf.array().copyOf(size)
    }

    @Test
    fun completeThreeWayHandshakeAndTearDown() {
        val responder = createResponder()
        val clientPort = 45678
        val clientIsn = 1000L

        // 1. SYN
        val synPacket = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = clientIsn,
            flags = TcpDnsResponder.FLAG_SYN
        )
        val synResponses = process(responder, synPacket)
        assertEquals(1, synResponses.size)
        val parsedSynAck = parseTcpPacket(synResponses[0])
        assertEquals(53, parsedSynAck.srcPort)
        assertEquals(clientPort, parsedSynAck.dstPort)
        assertEquals(TcpDnsResponder.FLAG_SYN or TcpDnsResponder.FLAG_ACK, parsedSynAck.flags)
        assertEquals(clientIsn + 1, parsedSynAck.ackNum)
        val serverIsn = parsedSynAck.seqNum
        assertEquals(1, responder.activeConnectionCount())

        // 2. ACK
        val ackPacket = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = clientIsn + 1,
            ackNum = serverIsn + 1,
            flags = TcpDnsResponder.FLAG_ACK
        )
        val ackResponses = process(responder, ackPacket)
        assertTrue(ackResponses.isEmpty())
        assertEquals(1, responder.activeConnectionCount())

        // 3. FIN
        val finPacket = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = clientIsn + 1,
            ackNum = serverIsn + 1,
            flags = TcpDnsResponder.FLAG_FIN or TcpDnsResponder.FLAG_ACK
        )
        val finResponses = process(responder, finPacket)
        assertEquals(1, finResponses.size)
        val parsedFinAck = parseTcpPacket(finResponses[0])
        assertEquals(TcpDnsResponder.FLAG_FIN or TcpDnsResponder.FLAG_ACK, parsedFinAck.flags)
        assertEquals(clientIsn + 2, parsedFinAck.ackNum)
        assertEquals(0, responder.activeConnectionCount())
    }

    @Test
    fun lengthPrefixedQueryAndPshAckResponse() {
        var queryReceived: ByteArray? = null
        val fakeDnsAnswer = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val responder = createResponder(queryResolver = { dnsWire, _, _ ->
                queryReceived = dnsWire
                fakeDnsAnswer
            })

        val clientPort = 50001
        val clientIsn = 2000L

        // SYN
        val syn = buildClientTcpPacket(srcPort = clientPort, seqNum = clientIsn, flags = TcpDnsResponder.FLAG_SYN)
        val synAck = parseTcpPacket(process(responder, syn).first())
        val serverIsn = synAck.seqNum

        // Send query with 2-byte length prefix
        val dnsWire = buildSyntheticDnsQuery("doubleclick.net")
        val prefixedQuery = ByteArray(2 + dnsWire.size)
        prefixedQuery[0] = ((dnsWire.size shr 8) and 0xFF).toByte()
        prefixedQuery[1] = (dnsWire.size and 0xFF).toByte()
        System.arraycopy(dnsWire, 0, prefixedQuery, 2, dnsWire.size)

        val psh = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = clientIsn + 1,
            ackNum = serverIsn + 1,
            flags = TcpDnsResponder.FLAG_PSH or TcpDnsResponder.FLAG_ACK,
            payload = prefixedQuery
        )
        val pshResponses = process(responder, psh)
        pshResponses.forEachIndexed { i, p -> println("Packet $i: flags=${parseTcpPacket(p).flags}") }
        assertEquals(2, pshResponses.size)
        assertNotNull(queryReceived)
        assertEquals(dnsWire.size, queryReceived!!.size)

        val parsedPshAck = parseTcpPacket(pshResponses[1])
        assertEquals(TcpDnsResponder.FLAG_PSH or TcpDnsResponder.FLAG_ACK, parsedPshAck.flags)
        assertEquals(clientIsn + 1 + prefixedQuery.size, parsedPshAck.ackNum)
        assertEquals(serverIsn + 1, parsedPshAck.seqNum)

        // Verify response payload has 2-byte length prefix + answer
        assertEquals(2 + fakeDnsAnswer.size, parsedPshAck.payload.size)
        val respLen = ((parsedPshAck.payload[0].toInt() and 0xFF) shl 8) or (parsedPshAck.payload[1].toInt() and 0xFF)
        assertEquals(fakeDnsAnswer.size, respLen)
        for (i in fakeDnsAnswer.indices) {
            assertEquals(fakeDnsAnswer[i], parsedPshAck.payload[2 + i])
        }
    }

    @Test
    fun clientRstClosesConnectionImmediately() {
        val responder = createResponder()
        val clientPort = 42000
        val syn = buildClientTcpPacket(srcPort = clientPort, seqNum = 100L, flags = TcpDnsResponder.FLAG_SYN)
        process(responder, syn)
        assertEquals(1, responder.activeConnectionCount())

        val rst = buildClientTcpPacket(srcPort = clientPort, seqNum = 101L, flags = TcpDnsResponder.FLAG_RST)
        val responses = process(responder, rst)
        assertTrue(responses.isEmpty())
        assertEquals(0, responder.activeConnectionCount())
    }

    @Test
    fun unexpectedTrafficOnUnknownConnectionReturnsRst() {
        val responder = createResponder()
        val unknownAck = buildClientTcpPacket(
            srcPort = 33333,
            seqNum = 500L,
            ackNum = 1000L,
            flags = TcpDnsResponder.FLAG_ACK
        )
        val responses = process(responder, unknownAck)
        assertEquals(1, responses.size)
        val parsedRst = parseTcpPacket(responses[0])
        assertEquals(TcpDnsResponder.FLAG_RST, parsedRst.flags)
        assertEquals(0, responder.activeConnectionCount())
    }

    @Test
    fun ignoresNonPort53OrWrongIpOrGarbagePackets() {
        val responder = createResponder()

        // Non-53 port to tunnel IP triggers R-4 fast RST|ACK
        val wrongPort = buildClientTcpPacket(dstPort = 80)
        val rstResp = process(responder, wrongPort)
        assertEquals(1, rstResp.size)
        assertEquals(TcpDnsResponder.FLAG_RST or TcpDnsResponder.FLAG_ACK, parseTcpPacket(rstResp[0]).flags)

        // Wrong IP (not tunnel IP) is completely ignored
        val wrongIp = buildClientTcpPacket(dstIp = byteArrayOf(8, 8, 8, 8))
        assertTrue(process(responder, wrongIp).isEmpty())

        // Too short garbage is completely ignored
        val garbage = byteArrayOf(1, 2, 3)
        assertTrue(process(responder, garbage).isEmpty())
    }

    @Test
    fun dropsOutOfOrderSegments() {
        val responder = createResponder()
        val clientPort = 41111
        val syn = buildClientTcpPacket(srcPort = clientPort, seqNum = 1000L, flags = TcpDnsResponder.FLAG_SYN)
        val synAck = parseTcpPacket(process(responder, syn).first())

        // Out-of-order segment with unexpected seqNum (9999 instead of 1001)
        val outOfOrder = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = 9999L,
            ackNum = synAck.seqNum + 1,
            flags = TcpDnsResponder.FLAG_PSH or TcpDnsResponder.FLAG_ACK,
            payload = byteArrayOf(0, 5, 1, 2, 3, 4, 5)
        )
        val responses = process(responder, outOfOrder)
        assertTrue(responses.isEmpty())
    }

    @Test
    fun bufferBoundExceededSendsRstAndCloses() {
        val responder = createResponder(maxBufferSize = 64)
        val clientPort = 42222
        val syn = buildClientTcpPacket(srcPort = clientPort, seqNum = 1000L, flags = TcpDnsResponder.FLAG_SYN)
        val synAck = parseTcpPacket(process(responder, syn).first())

        // Overflow buffer (>64 bytes)
        val overflowPayload = ByteArray(100) { 0xAA.toByte() }
        val overflowPacket = buildClientTcpPacket(
            srcPort = clientPort,
            seqNum = 1001L,
            ackNum = synAck.seqNum + 1,
            flags = TcpDnsResponder.FLAG_PSH or TcpDnsResponder.FLAG_ACK,
            payload = overflowPayload
        )
        val responses = process(responder, overflowPacket)
        assertEquals(1, responses.size)
        val parsedRst = parseTcpPacket(responses[0])
        assertEquals(TcpDnsResponder.FLAG_RST or TcpDnsResponder.FLAG_ACK, parsedRst.flags)
        assertEquals(0, responder.activeConnectionCount())
    }

    @Test
    fun purgesIdleConnectionsAfterTimeout() {
        val responder = createResponder(idleTimeoutMillis = 50L)
        val syn = buildClientTcpPacket(srcPort = 43333, seqNum = 1000L, flags = TcpDnsResponder.FLAG_SYN)
        process(responder, syn)
        assertEquals(1, responder.activeConnectionCount())

        Thread.sleep(60L)

        // New packet triggers cleanup
        val syn2 = buildClientTcpPacket(srcPort = 43334, seqNum = 2000L, flags = TcpDnsResponder.FLAG_SYN)
        process(responder, syn2)
        assertEquals(1, responder.activeConnectionCount())
    }

    @Test
    fun synFloodBoundEnforcesMaxConcurrentConnections() {
        val responder = createResponder(maxConnections = 32)

        for (port in 10001..10032) {
            val syn = buildClientTcpPacket(srcPort = port, seqNum = port.toLong(), flags = TcpDnsResponder.FLAG_SYN)
            val resp = process(responder, syn)
            assertEquals(1, resp.size)
            val parsed = parseTcpPacket(resp[0])
            assertEquals(TcpDnsResponder.FLAG_SYN or TcpDnsResponder.FLAG_ACK, parsed.flags)
        }
        assertEquals(32, responder.activeConnectionCount())

        // 33rd connection SYN should be rejected with RST
        val syn33 = buildClientTcpPacket(srcPort = 10033, seqNum = 10033L, flags = TcpDnsResponder.FLAG_SYN)
        val resp33 = process(responder, syn33)
        assertEquals(1, resp33.size)
        val parsedRst = parseTcpPacket(resp33[0])
        assertEquals(TcpDnsResponder.FLAG_RST or TcpDnsResponder.FLAG_ACK, parsedRst.flags)
        assertEquals(32, responder.activeConnectionCount())
    }

    @Test
    fun completeIpv6TcpDnsExchange() {
        val dnsWire = buildSyntheticDnsQuery("ipv6.example.org", txId = 0x5678.toShort(), type = 28) // AAAA
        val fakeAnswer = byteArrayOf(0x56, 0x78.toByte(), 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val responder = createResponder(queryResolver = { wire, isIpv6, clientIp ->
                assertTrue(isIpv6)
                assertEquals(16, clientIp.size)
                fakeAnswer
            })

        val clientPort = 56789
        val clientIsn = 5000L

        // 1. IPv6 SYN
        val syn = buildClientTcpPacket(
            isIpv6 = true,
            srcPort = clientPort,
            seqNum = clientIsn,
            flags = TcpDnsResponder.FLAG_SYN
        )
        val synResponses = process(responder, syn)
        assertEquals(1, synResponses.size)
        val parsedSynAck = parseTcpPacket(synResponses[0])
        assertTrue(parsedSynAck.isIpv6)
        assertEquals(53, parsedSynAck.srcPort)
        assertEquals(clientPort, parsedSynAck.dstPort)
        assertEquals(TcpDnsResponder.FLAG_SYN or TcpDnsResponder.FLAG_ACK, parsedSynAck.flags)
        val serverIsn = parsedSynAck.seqNum

        // 2. IPv6 Query
        val prefixed = ByteArray(2 + dnsWire.size)
        prefixed[0] = ((dnsWire.size shr 8) and 0xFF).toByte()
        prefixed[1] = (dnsWire.size and 0xFF).toByte()
        System.arraycopy(dnsWire, 0, prefixed, 2, dnsWire.size)

        val psh = buildClientTcpPacket(
            isIpv6 = true,
            srcPort = clientPort,
            seqNum = clientIsn + 1,
            ackNum = serverIsn + 1,
            flags = TcpDnsResponder.FLAG_PSH or TcpDnsResponder.FLAG_ACK,
            payload = prefixed
        )
        val pshResponses = process(responder, psh)
        pshResponses.forEachIndexed { i, p -> println("Packet $i: flags=${parseTcpPacket(p).flags}") }
        assertEquals(2, pshResponses.size)
        val parsedPshAck = parseTcpPacket(pshResponses[1])
        assertTrue(parsedPshAck.isIpv6)
        assertEquals(2 + fakeAnswer.size, parsedPshAck.payload.size)

        // 3. IPv6 FIN
        val fin = buildClientTcpPacket(
            isIpv6 = true,
            srcPort = clientPort,
            seqNum = clientIsn + 1 + prefixed.size,
            ackNum = parsedPshAck.seqNum + parsedPshAck.payload.size,
            flags = TcpDnsResponder.FLAG_FIN or TcpDnsResponder.FLAG_ACK
        )
        val finResponses = process(responder, fin)
        assertEquals(1, finResponses.size)
        val parsedFinAck = parseTcpPacket(finResponses[0])
        assertTrue(parsedFinAck.isIpv6)
        assertEquals(TcpDnsResponder.FLAG_FIN or TcpDnsResponder.FLAG_ACK, parsedFinAck.flags)
        assertEquals(0, responder.activeConnectionCount())
    }

    @Test
    fun parseWireQueryAndBuildBlockedWireResponse() {
        val queryWire = buildSyntheticDnsQuery("adserver.tracking.com", txId = 0x4321.toShort(), type = 1)
        val query = DnsPacketUtil.parseWireQuery(queryWire, isIpv6 = false, clientIp = ipv4Client)
        assertNotNull(query)
        assertEquals("adserver.tracking.com", query!!.domain)
        assertEquals(1.toShort(), query.queryType)
        assertEquals(0x4321.toShort(), query.transactionId)

        val blockedWire = DnsPacketUtil.buildBlockedWireResponse(queryWire)
        assertNotNull(blockedWire)
        assertTrue(blockedWire!!.size >= 12)
        assertEquals(0x4321.toShort(), ((blockedWire[0].toInt() and 0xFF) shl 8 or (blockedWire[1].toInt() and 0xFF)).toShort())
        // Flags: 0x8580 (QR=1, AA=1, RA=1, RCODE=0)
        val flags = ((blockedWire[2].toInt() and 0xFF) shl 8) or (blockedWire[3].toInt() and 0xFF)
        assertEquals(0x8580, flags)
        // ANCOUNT = 1
        val anCount = ((blockedWire[6].toInt() and 0xFF) shl 8) or (blockedWire[7].toInt() and 0xFF)
        assertEquals(1, anCount)
    }

    @Test
    fun dohCanaryReturnsNxdomainInBlockedWireResponse() {
        val canaryWire = buildSyntheticDnsQuery("use-application-dns.net", txId = 0x9999.toShort(), type = 1)
        val blockedCanary = DnsPacketUtil.buildBlockedWireResponse(canaryWire)
        assertNotNull(blockedCanary)
        val flags = ((blockedCanary!![2].toInt() and 0xFF) shl 8) or (blockedCanary[3].toInt() and 0xFF)
        assertEquals(0x8583, flags) // NXDOMAIN (rcode = 3)
        val anCount = ((blockedCanary[6].toInt() and 0xFF) shl 8) or (blockedCanary[7].toInt() and 0xFF)
        assertEquals(0, anCount)
    }

    @Test
    fun testDoTNonDnsPortSynEmitsFastRst() {
        val responder = createResponder()
        val synDot = buildClientTcpPacket(
            srcPort = 54321,
            dstPort = 853,
            seqNum = 12345L,
            flags = TcpDnsResponder.FLAG_SYN
        )
        val responses = process(responder, synDot)
        assertEquals(1, responses.size)
        val parsed = parseTcpPacket(responses[0])
        assertEquals(TcpDnsResponder.FLAG_RST or TcpDnsResponder.FLAG_ACK, parsed.flags)
        assertEquals(853, parsed.srcPort)
        assertEquals(54321, parsed.dstPort)
    }

    @Test
    fun testNonTunnelIpReturnsFalseAndEmitsNothing() {
        val responder = createResponder()
        val foreignPacket = buildClientTcpPacket(
            dstIp = byteArrayOf(1, 1, 1, 1),
            dstPort = 53
        )
        val handled = responder.processPacket(foreignPacket, foreignPacket.size)
        org.junit.Assert.assertFalse(handled)
        assertTrue(emittedPackets.isEmpty())
    }
}
