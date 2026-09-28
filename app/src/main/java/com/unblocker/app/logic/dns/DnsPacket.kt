package com.unblocker.app.logic.dns

import com.unblocker.app.logic.DomainName
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
    val dstPort: Int,
    val isIpv6: Boolean = false
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

    /** Returns the lowest positive answer TTL, or null when the payload is not cacheable. */
    fun minCacheTtlSeconds(payload: ByteArray, length: Int = payload.size): Long? {
        if (length < 12 || length > payload.size) return null
        val flags = u16(payload, 2)
        if (flags and 0x8000 == 0 || flags and 0x000f != 0) return null
        val questionCount = u16(payload, 4)
        val answerCount = u16(payload, 6)
        if (answerCount == 0) return null

        var position = 12
        repeat(questionCount) {
            position = skipDnsName(payload, position, length) ?: return null
            if (position + 4 > length) return null
            position += 4
        }

        var minimum: Long? = null
        repeat(answerCount) {
            position = skipDnsName(payload, position, length) ?: return null
            if (position + 10 > length) return null
            val ttl = u32(payload, position + 4)
            val dataLength = u16(payload, position + 8)
            position += 10
            if (position + dataLength > length) return null
            position += dataLength
            minimum = minimum?.coerceAtMost(ttl) ?: ttl
        }
        return minimum?.takeIf { it > 0 }
    }

    private fun skipDnsName(payload: ByteArray, start: Int, end: Int): Int? {
        var position = start
        while (position < end) {
            val length = payload[position].toInt() and 0xff
            when {
                length == 0 -> return position + 1
                length and 0xc0 == 0xc0 -> return if (position + 1 < end) position + 2 else null
                length > 63 || position + 1 + length > end -> return null
                else -> position += 1 + length
            }
        }
        return null
    }

    private fun u16(payload: ByteArray, offset: Int): Int =
        ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)

    private fun u32(payload: ByteArray, offset: Int): Long =
        ((payload[offset].toLong() and 0xff) shl 24) or
            ((payload[offset + 1].toLong() and 0xff) shl 16) or
            ((payload[offset + 2].toLong() and 0xff) shl 8) or
            (payload[offset + 3].toLong() and 0xff)

    /**
     * Parse IPv4/UDP packet containing a DNS query from the TUN interface.
     * Returns null if the packet is not IPv4 UDP DNS query.
     */
    fun parseIpPacket(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // 20 IP + 8 UDP minimum

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        val isIpv6 = (version == 6)
        if (version != 4 && version != 6) return null

        val (udpOffset, srcIp, dstIp) = if (isIpv6) {
            if (length < 48) return null // 40 IPv6 + 8 UDP
            val nextHeader = packet[6].toInt() and 0xFF
            if (nextHeader != 17) return null // Protocol 17 = UDP
            val src = ByteArray(16) { packet[8 + it] }
            val dst = ByteArray(16) { packet[24 + it] }
            Triple(40, src, dst)
        } else {
            val ihl = (versionAndIhl and 0x0F) * 4
            if (ihl < 20 || length < ihl + 8) return null // RFC 791 requires min IPv4 IHL of 5 (20 bytes)
            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return null // Protocol 17 = UDP
            val src = ByteArray(4) { packet[12 + it] }
            val dst = ByteArray(4) { packet[16 + it] }
            Triple(ihl, src, dst)
        }

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        // DNS is typically port 53
        if (dstPort != 53) return null

        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLength = udpLength - 8

        if (udpLength < 20 || dnsLength < 12 || dnsOffset + dnsLength > length) return null

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

        val domain = DomainName.normalize(domainBuilder.toString()) ?: return null

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
            dstPort = dstPort,
            isIpv6 = isIpv6
        )
    }

    /**
     * Builds a synthetic DNS response returning 0.0.0.0 (or :: for AAAA)
     * wrapped in a complete IPv4/UDP packet ready to write to the TUN interface.
     */
    fun buildBlockedDnsResponsePacket(query: DnsQuery): ByteArray {
        val isDoHCanary = query.domain == "use-application-dns.net"
        val isA = query.queryType == TYPE_A
        val isAaaa = query.queryType == TYPE_AAAA

        // DNS Response Body
        val questionBytes = extractQuestionSection(query.rawPacket, query.dnsOffset, query.dnsLength)
        val answerRecordLength = if (isDoHCanary) 0 else if (isA) 16 else if (isAaaa) 28 else 0

        val dnsResponseSize = 12 + questionBytes.size + answerRecordLength
        val udpLength = 8 + dnsResponseSize
        val dnsFlags = if (isDoHCanary) 0x8583.toShort() else 0x8580.toShort() // NXDOMAIN (3) or NoError (0)

        return if (query.isIpv6) {
            val ipTotalLength = 40 + udpLength
            val responseBuffer = ByteBuffer.allocate(ipTotalLength)
            responseBuffer.order(ByteOrder.BIG_ENDIAN)

            // 1. IPv6 Header (40 bytes)
            responseBuffer.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
            responseBuffer.putShort(udpLength.toShort()) // Payload length
            responseBuffer.put(17.toByte()) // Next Header: UDP
            responseBuffer.put(64.toByte()) // Hop Limit
            responseBuffer.put(query.dstIp) // Src IP (16 bytes, original dst)
            responseBuffer.put(query.srcIp) // Dst IP (16 bytes, original src)

            // 2. UDP Header (8 bytes)
            responseBuffer.putShort(query.dstPort.toShort()) // Src Port (53)
            responseBuffer.putShort(query.srcPort.toShort()) // Dst Port
            responseBuffer.putShort(udpLength.toShort()) // UDP length
            responseBuffer.putShort(0x0000.toShort()) // Checksum

            // 3. DNS Header (12 bytes)
            responseBuffer.putShort(query.transactionId) // ID
            responseBuffer.putShort(dnsFlags)
            responseBuffer.putShort(1.toShort()) // QDCOUNT: 1
            responseBuffer.putShort((if (answerRecordLength > 0) 1 else 0).toShort()) // ANCOUNT
            responseBuffer.putShort(0.toShort()) // NSCOUNT: 0
            responseBuffer.putShort(0.toShort()) // ARCOUNT: 0

            // 4. DNS Question
            responseBuffer.put(questionBytes)

            // 5. DNS Answer (if A or AAAA and not canary)
            if (!isDoHCanary) {
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
            }

            responseBuffer.array()
        } else {
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
            responseBuffer.putShort(dnsFlags)
            responseBuffer.putShort(1.toShort()) // QDCOUNT: 1
            responseBuffer.putShort((if (answerRecordLength > 0) 1 else 0).toShort()) // ANCOUNT
            responseBuffer.putShort(0.toShort()) // NSCOUNT: 0
            responseBuffer.putShort(0.toShort()) // ARCOUNT: 0

            // 4. DNS Question
            responseBuffer.put(questionBytes)

            // 5. DNS Answer (if A or AAAA and not canary)
            if (!isDoHCanary) {
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
            }

            responseBuffer.array()
        }
    }

    private fun extractQuestionSection(packet: ByteArray, dnsOffset: Int, dnsLength: Int): ByteArray {
        if (dnsLength < 12) return ByteArray(0)
        val qStart = dnsOffset + 12
        var pos = qStart
        val end = dnsOffset + dnsLength
        while (pos < end) {
            val len = packet[pos].toInt() and 0xFF
            pos++
            if (len == 0) break
            if (len > 63 || pos + len > end) {
                pos = end
                break
            }
            pos += len
        }
        pos += 4 // QTYPE (2) + QCLASS (2)
        val maxAvailable = (end - qStart).coerceAtLeast(0)
        val qLength = (pos - qStart).coerceIn(0, maxAvailable)
        val question = ByteArray(qLength)
        if (qLength > 0 && qStart + qLength <= packet.size) {
            System.arraycopy(packet, qStart, question, 0, qLength)
        }
        return question
    }

    private fun computeIpChecksum(buf: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        val end = offset + length
        var i = offset
        while (i < end - 1) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }
}
