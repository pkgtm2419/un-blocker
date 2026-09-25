package com.unblocker.app.logic.dns

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsQuery(
    val transactionId: Short,
    val domain: String,
    val queryType: Short,
    val queryClass: Short,
    val rawPacket: ByteArray,
    val dnsOffset: Int,
    val dnsLength: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DnsQuery
        return transactionId == other.transactionId && domain == other.domain
    }

    override fun hashCode(): Int {
        var result = transactionId.toInt()
        result = 31 * result + domain.hashCode()
        return result
    }
}

object DnsPacketUtil {

    const val TYPE_A: Short = 1
    const val TYPE_AAAA: Short = 28
    const val CLASS_IN: Short = 1

    /**
     * Parse IPv4/UDP packet containing a DNS query from the TUN interface.
     * Returns null if the packet is not IPv4 UDP DNS query.
     */
    fun parseIpPacket(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // 20 IP + 8 UDP minimum

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null // Only IPv4 handled for local DNS spoofing

        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // Protocol 17 = UDP

        val srcIp = ByteArray(4) { packet[12 + it] }
        val dstIp = ByteArray(4) { packet[16 + it] }

        val udpOffset = ihl
        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        // DNS is typically port 53
        if (dstPort != 53) return null

        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLength = udpLength - 8

        if (dnsOffset + dnsLength > length || dnsLength < 12) return null

        // Parse DNS Header
        val txId = ((packet[dnsOffset].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 1].toInt() and 0xFF)).toShort()
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 3].toInt() and 0xFF))
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 5].toInt() and 0xFF))
        if (qdCount < 1) return null

        // Parse QNAME
        var pos = dnsOffset + 12
        val domainBuilder = StringBuilder()
        val dnsEnd = dnsOffset + dnsLength

        while (pos < dnsEnd) {
            val labelLen = packet[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break // End of domain
            if (labelLen > 63) return null // Compression in query not standard

            if (pos + labelLen > dnsEnd) return null

            if (domainBuilder.isNotEmpty()) {
                domainBuilder.append('.')
            }
            domainBuilder.append(String(packet, pos, labelLen, Charsets.US_ASCII))
            pos += labelLen
        }

        if (pos + 4 > dnsEnd) return null
        val qType = ((packet[pos].toInt() and 0xFF) shl 8 or (packet[pos + 1].toInt() and 0xFF)).toShort()
        val qClass = ((packet[pos + 2].toInt() and 0xFF) shl 8 or (packet[pos + 3].toInt() and 0xFF)).toShort()

        val domain = domainBuilder.toString().lowercase()
        if (domain.isBlank()) return null

        return DnsQuery(
            transactionId = txId,
            domain = domain,
            queryType = qType,
            queryClass = qClass,
            rawPacket = packet,
            dnsOffset = dnsOffset,
            dnsLength = dnsLength,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort
        )
    }

    /**
     * Builds a synthetic DNS response returning 0.0.0.0 (or :: for AAAA)
     * wrapped in a complete IPv4/UDP packet ready to write to the TUN interface.
     */
    fun buildBlockedDnsResponsePacket(query: DnsQuery): ByteArray {
        val isA = query.queryType == TYPE_A
        val isAaaa = query.queryType == TYPE_AAAA

        // DNS Response Body
        val questionBytes = extractQuestionSection(query.rawPacket, query.dnsOffset, query.dnsLength)
        val answerRecordLength = if (isA) 16 else if (isAaaa) 28 else 0

        val dnsResponseSize = 12 + questionBytes.size + answerRecordLength
        val udpLength = 8 + dnsResponseSize
        val ipTotalLength = 20 + udpLength

        val responseBuffer = ByteBuffer.allocate(ipTotalLength)
        responseBuffer.order(ByteOrder.BIG_ENDIAN)

        // 1. IPv4 Header (20 bytes)
        responseBuffer.put(0x45.toByte()) // Version 4, IHL 5
        responseBuffer.put(0x00.toByte()) // DSCP/ECN
        responseBuffer.putShort(ipTotalLength.toShort()) // Total length
        responseBuffer.putShort(0x0000.toShort()) // Identification
        responseBuffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
        responseBuffer.put(64.toByte()) // TTL
        responseBuffer.put(17.toByte()) // Protocol: UDP
        responseBuffer.putShort(0x0000.toShort()) // Checksum placeholder
        responseBuffer.put(query.dstIp) // Src IP (was original dst)
        responseBuffer.put(query.srcIp) // Dst IP (was original src)

        // Compute and insert IP checksum
        val ipChecksum = computeIpChecksum(responseBuffer.array(), 0, 20)
        responseBuffer.putShort(10, ipChecksum)

        // 2. UDP Header (8 bytes)
        responseBuffer.position(20)
        responseBuffer.putShort(query.dstPort.toShort()) // Src Port (53)
        responseBuffer.putShort(query.srcPort.toShort()) // Dst Port
        responseBuffer.putShort(udpLength.toShort()) // UDP length
        responseBuffer.putShort(0x0000.toShort()) // Checksum (optional in IPv4 UDP, 0 = disabled)

        // 3. DNS Header (12 bytes)
        responseBuffer.putShort(query.transactionId) // ID
        // Flags: QR=1 (Response), Opcode=0, AA=1, TC=0, RD=1, RA=1, Z=0, RCODE=0 (NoError) -> 0x8580
        responseBuffer.putShort(0x8580.toShort())
        responseBuffer.putShort(1.toShort()) // QDCOUNT: 1
        responseBuffer.putShort((if (answerRecordLength > 0) 1 else 0).toShort()) // ANCOUNT
        responseBuffer.putShort(0.toShort()) // NSCOUNT: 0
        responseBuffer.putShort(0.toShort()) // ARCOUNT: 0

        // 4. DNS Question
        responseBuffer.put(questionBytes)

        // 5. DNS Answer (if A or AAAA)
        if (isA) {
            responseBuffer.putShort(0xC00C.toShort()) // Name pointer to byte 12 (QNAME)
            responseBuffer.putShort(TYPE_A)
            responseBuffer.putShort(CLASS_IN)
            responseBuffer.putInt(300) // TTL: 300 seconds
            responseBuffer.putShort(4.toShort()) // RDLENGTH: 4 bytes
            responseBuffer.put(byteArrayOf(0, 0, 0, 0)) // 0.0.0.0
        } else if (isAaaa) {
            responseBuffer.putShort(0xC00C.toShort())
            responseBuffer.putShort(TYPE_AAAA)
            responseBuffer.putShort(CLASS_IN)
            responseBuffer.putInt(300)
            responseBuffer.putShort(16.toShort())
            responseBuffer.put(ByteArray(16)) // :: (unspecified / null IPv6)
        }

        return responseBuffer.array()
    }

    private fun extractQuestionSection(packet: ByteArray, dnsOffset: Int, dnsLength: Int): ByteArray {
        val qStart = dnsOffset + 12
        var pos = qStart
        val end = dnsOffset + dnsLength
        while (pos < end) {
            val len = packet[pos].toInt() and 0xFF
            pos++
            if (len == 0) break
            pos += len
        }
        pos += 4 // QTYPE (2) + QCLASS (2)
        val qLength = (pos - qStart).coerceAtMost(end - qStart)
        val question = ByteArray(qLength)
        System.arraycopy(packet, qStart, question, 0, qLength)
        return question
    }

    private fun computeIpChecksum(buf: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        for (i in offset until offset + length step 2) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }
}
