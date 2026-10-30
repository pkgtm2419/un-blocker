package com.unblocker.app.dns

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import com.unblocker.app.logic.dns.DnsResponseValidator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class DnsFuzzMutationTest {

    private val rng = Random(42)

    @Test
    fun testFuzzRandomGarbageBytePayloads() {
        // Feed 500 pseudo-random byte arrays of arbitrary sizes through all packet parser entry points
        for (i in 0 until 500) {
            val length = rng.nextInt(2048)
            val garbage = ByteArray(length)
            rng.nextBytes(garbage)

            // Must reject cleanly without IndexOutOfBoundsException or crash
            val parsedIp = DnsPacketUtil.parseIpPacket(garbage, length)
            assertNull(parsedIp)

            val parsedWireV4 = DnsPacketUtil.parseWireQuery(garbage, false, byteArrayOf(10, 10, 0, 2))
            assertNull(parsedWireV4)

            val parsedWireV6 = DnsPacketUtil.parseWireQuery(garbage, true, ByteArray(16))
            assertNull(parsedWireV6)

            val servfailWire = DnsPacketUtil.buildServfailWireResponse(garbage)
            assertNull(servfailWire)
        }
    }

    @Test
    fun testFuzzMalformedIpv4AndIpv6Headers() {
        val baseV4 = ByteArray(60)
        baseV4[0] = 0x45.toByte() // IPv4, IHL 5
        baseV4[9] = 17.toByte() // UDP
        baseV4[18] = 0.toByte(); baseV4[19] = 53.toByte() // dstPort 53
        baseV4[20] = 0.toByte(); baseV4[21] = 40.toByte() // udpLength 40

        // Corrupt length and offsets
        for (corruptLength in listOf(-1, 0, 1, 19, 27, 28, 59, 100)) {
            assertNull(DnsPacketUtil.parseIpPacket(baseV4, corruptLength))
        }

        // Test invalid IPv4 IHL values (< 5 or > packet length)
        for (ihl in 0..4) {
            baseV4[0] = (0x40 or ihl).toByte()
            assertNull(DnsPacketUtil.parseIpPacket(baseV4, 60))
        }

        // Test IPv6 with invalid payload length or next header
        val baseV6 = ByteArray(80)
        baseV6[0] = 0x60.toByte()
        baseV6[6] = 17.toByte() // Next header UDP
        baseV6[42] = 0.toByte(); baseV6[43] = 53.toByte() // dstPort 53
        for (corruptLen in listOf(0, 1, 39, 47)) {
            assertNull(DnsPacketUtil.parseIpPacket(baseV6, corruptLen))
        }
    }

    @Test
    fun testFuzzCompressionPointerLoops() {
        // Construct DNS wire packet with cyclic compression pointer
        // DNS header (12 bytes) + question label pointing to itself
        val cyclic = ByteArray(20)
        val buf = ByteBuffer.wrap(cyclic).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x1234.toShort())
        buf.putShort(0x0100.toShort()) // standard query
        buf.putShort(1.toShort()) // QDCOUNT = 1
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        // Pointer 0xC00C points to offset 12 (itself!)
        buf.put(0xC0.toByte())
        buf.put(0x0C.toByte())
        buf.putShort(1.toShort()) // QTYPE
        buf.putShort(1.toShort()) // QCLASS

        // Must reject cyclic pointer without StackOverflow or infinite loop
        val result = DnsPacketUtil.parseWireQuery(cyclic, false, byteArrayOf(10, 10, 0, 2))
        assertNull("Cyclic compression pointer must be rejected", result)
    }

    @Test
    fun testFuzzOversizedLabelsAndHostnames() {
        // Label length > 63 is illegal in DNS RFC 1035
        val packet = ByteArray(100)
        packet[0] = 64.toByte() // illegal label length 64
        for (i in 1..64) packet[i] = 'a'.code.toByte()
        packet[65] = 0.toByte()

        val fullWire = ByteBuffer.allocate(12 + packet.size + 4).order(ByteOrder.BIG_ENDIAN)
        fullWire.putShort(0x9999.toShort())
        fullWire.putShort(0x0100.toShort())
        fullWire.putShort(1.toShort())
        fullWire.putShort(0.toShort()); fullWire.putShort(0.toShort()); fullWire.putShort(0.toShort())
        fullWire.put(packet)
        fullWire.putShort(1.toShort()); fullWire.putShort(1.toShort())

        val result = DnsPacketUtil.parseWireQuery(fullWire.array(), false, byteArrayOf(10, 10, 0, 2))
        assertNull("Label length > 63 must be rejected", result)
    }

    @Test
    fun testFuzzDnsResponseValidatorCorruptedPackets() {
        val query = DnsQuery(
            transactionId = 0x1234,
            domain = "fuzz.example.com",
            queryType = 1,
            queryClass = 1,
            rawPacket = ByteArray(0),
            dnsOffset = 0,
            dnsLength = 0,
            srcIp = byteArrayOf(10, 10, 0, 2),
            dstIp = byteArrayOf(10, 10, 0, 1),
            srcPort = 12345,
            dstPort = 53
        )

        // Mutate arbitrary bits in a synthetic response buffer
        val baseResponse = ByteArray(128)
        ByteBuffer.wrap(baseResponse).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0x1234.toShort()) // txId
            putShort(0x8180.toShort()) // response flags
            putShort(1.toShort()) // qdcount
            putShort(1.toShort()) // ancount
        }

        for (seed in 0..100) {
            val mutated = baseResponse.clone()
            val mRng = Random(seed.toLong())
            val mutations = mRng.nextInt(10) + 1
            for (m in 0 until mutations) {
                val pos = mRng.nextInt(mutated.size)
                mutated[pos] = (mutated[pos].toInt() xor (1 shl mRng.nextInt(8))).toByte()
            }
            // Must not throw unhandled runtime exceptions
            try {
                DnsResponseValidator.parseAndValidate(mutated, mutated.size, query)
            } catch (e: Exception) {
                throw AssertionError("DnsResponseValidator threw unexpected exception on mutated input: ${e::class.java.name}", e)
            }
        }
    }
}
