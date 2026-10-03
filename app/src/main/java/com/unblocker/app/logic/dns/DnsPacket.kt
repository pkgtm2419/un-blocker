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
    val isIpv6: Boolean = false,
    val udpPayloadLimit: Int = 512,
    val hasEdns: Boolean = false,
    val ednsFlags: Int = 0,
    val cacheVariant: String = "",
    val cacheAllowed: Boolean = true
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

    /** Largest DNS body that can be represented by the returned IP/UDP packet. */
    fun maxDnsPayloadLength(query: DnsQuery): Int =
        if (query.isIpv6) 0xffff - 8 else 0xffff - 20 - 8

    /** Returns the lowest positive answer TTL, or null when the payload is not cacheable. */
    fun minCacheTtlSeconds(payload: ByteArray, length: Int = payload.size): Long? =
        DnsResponseValidator.parseMetadata(payload, length)
            ?.takeIf { !it.truncated && it.rcode == 0 }
            ?.minPositiveTtlSeconds

    /**
     * Parse IPv4/UDP packet containing a DNS query from the TUN interface.
     * Returns null if the packet is not IPv4 UDP DNS query.
     */
    fun parseIpPacket(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28 || length > packet.size) return null

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        val isIpv6 = (version == 6)
        if (version != 4 && version != 6) return null

        val (udpOffset, srcIp, dstIp) = if (isIpv6) {
            if (length < 48) return null // 40 IPv6 + 8 UDP
            if (u16(packet,4) == 0 || 40 + u16(packet,4) != length) return null
            var nextHeader = packet[6].toInt() and 0xFF
            var offset = 40
            var hops = 0
            while (nextHeader != 17) {
                if (hops++ >= 8 || offset + 8 > length) return null
                val extensionLength = when (nextHeader) {
                    0,43,60 -> {
                        if (nextHeader == 0 && offset != 40) return null
                        ((packet[offset+1].toInt() and 255)+1)*8
                    }
                    44 -> {
                        if (packet[offset+1].toInt() != 0 || u16(packet,offset+2) != 0) return null
                        8 // Atomic fragments only; no reassembly or fragmented transport.
                    }
                    else -> return null
                }
                if (offset + extensionLength + 8 > length) return null
                nextHeader = packet[offset].toInt() and 255
                offset += extensionLength
            }
            val src = ByteArray(16) { packet[8 + it] }
            val dst = ByteArray(16) { packet[24 + it] }
            Triple(offset, src, dst)
        } else {
            val ihl = (versionAndIhl and 0x0F) * 4
            if (ihl < 20 || length < ihl + 8) return null // RFC 791 requires min IPv4 IHL of 5 (20 bytes)
            if (u16(packet,2) != length || u16(packet,6) and 0xbfff != 0) return null
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

        if (udpLength < 20 || dnsLength < 12 || dnsOffset + dnsLength != length) return null

        // Parse DNS Header
        val txId = ((packet[dnsOffset].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 1].toInt() and 0xFF)).toShort()
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 3].toInt() and 0xFF))
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery || flags and 0x7800 != 0) return null

        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 5].toInt() and 0xFF))
        if (qdCount != 1 || u16(packet,dnsOffset+6) != 0 || u16(packet,dnsOffset+8) != 0) return null

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

            val label = DomainName.canonicalWireLabel(packet, pos, labelLen) ?: return null
            if (domainBuilder.isNotEmpty()) {
                domainBuilder.append('.')
            }
            domainBuilder.append(label)
            pos += labelLen
        }

        if (pos + 4 > dnsEnd) return null
        val qType = ((packet[pos].toInt() and 0xFF) shl 8 or (packet[pos + 1].toInt() and 0xFF)).toShort()
        val qClass = ((packet[pos + 2].toInt() and 0xFF) shl 8 or (packet[pos + 3].toInt() and 0xFF)).toShort()

        pos += 4
        val additional = u16(packet,dnsOffset+10)
        var hasEdns = false
        var udpLimit = 512
        var ednsFlags = 0
        var optionKey = "none"
        var cacheAllowed = true
        if (additional == 1) {
            if (pos+11>dnsEnd || packet[pos].toInt()!=0 || u16(packet,pos+1)!=41 ||
                packet[pos+5].toInt()!=0 || packet[pos+6].toInt()!=0) return null
            val dataSize = u16(packet,pos+9)
            if (pos+11+dataSize != dnsEnd || !validEdnsOptions(packet,pos+11,dnsEnd)) return null
            hasEdns=true
            udpLimit=u16(packet,pos+3).coerceAtLeast(512)
            ednsFlags=u16(packet,pos+7)
            cacheAllowed=dataSize<=256
            optionKey=if(cacheAllowed) "edns:$ednsFlags:" + (pos+11 until dnsEnd).joinToString("") {
                val byte=packet[it].toInt() and 255
                "0123456789abcdef"[byte ushr 4].toString()+"0123456789abcdef"[byte and 15]
            } else "uncacheable"
            pos=dnsEnd
        } else if(additional!=0) return null
        if(pos!=dnsEnd) return null

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
            isIpv6 = isIpv6,
            hasEdns = hasEdns,
            udpPayloadLimit = udpLimit,
            ednsFlags = ednsFlags,
            cacheVariant = "$flags:$optionKey",
            cacheAllowed = cacheAllowed
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

        val opt = if(query.hasEdns) minimalOpt(query) else byteArrayOf()
        val dnsResponseSize = 12 + questionBytes.size + answerRecordLength + opt.size
        val dnsFlags = if (isDoHCanary) 0x8583.toShort() else 0x8580.toShort() // NXDOMAIN (3) or NoError (0)
        val dnsResponse = ByteBuffer.allocate(dnsResponseSize).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(query.transactionId)
        dnsResponse.putShort(dnsFlags)
        dnsResponse.putShort(1.toShort())
        dnsResponse.putShort((if (answerRecordLength > 0) 1 else 0).toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort((if(query.hasEdns) 1 else 0).toShort())
        dnsResponse.put(questionBytes)

        if (!isDoHCanary && isA) {
            dnsResponse.putShort(0xC00C.toShort())
            dnsResponse.putShort(TYPE_A)
            dnsResponse.putShort(CLASS_IN)
            dnsResponse.putInt(300)
            dnsResponse.putShort(4.toShort())
            dnsResponse.put(byteArrayOf(0, 0, 0, 0))
        } else if (!isDoHCanary && isAaaa) {
            dnsResponse.putShort(0xC00C.toShort())
            dnsResponse.putShort(TYPE_AAAA)
            dnsResponse.putShort(CLASS_IN)
            dnsResponse.putInt(300)
            dnsResponse.putShort(16.toShort())
            dnsResponse.put(ByteArray(16))
        }

        dnsResponse.put(opt)
        return wrapDnsResponseInIpUdp(query, dnsResponse.array(), dnsResponseSize)
    }

    /** Re-negotiate each transaction; never send a cached oversized UDP body. */
    fun wrapClientResponse(query:DnsQuery,bytes:ByteArray):ByteArray {
        val limit=minOf(query.udpPayloadLimit.coerceAtLeast(512),maxDnsPayloadLength(query))
        if(bytes.size<=limit) return wrapDnsResponseInIpUdp(query,bytes,bytes.size)
        require(bytes.size>=12)
        val question=extractQuestionSection(query.rawPacket,query.dnsOffset,query.dnsLength)
        val opt=if(query.hasEdns) minimalOpt(query) else byteArrayOf()
        val truncated=ByteBuffer.allocate(12+question.size+opt.size).order(ByteOrder.BIG_ENDIAN)
        truncated.putShort(query.transactionId)
        truncated.putShort(((u16(bytes,2) or 0x8200) and 0xffdf).toShort()) // Clear AD on synthetic TC.
        truncated.putShort(1);truncated.putShort(0);truncated.putShort(0)
        truncated.putShort((if(query.hasEdns)1 else 0).toShort());truncated.put(question);truncated.put(opt)
        return wrapDnsResponseInIpUdp(query,truncated.array(),truncated.capacity())
    }
    private fun minimalOpt(query:DnsQuery):ByteArray = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN).apply {
        put(0);putShort(41);putShort(minOf(query.udpPayloadLimit,maxDnsPayloadLength(query)).toShort())
        putInt(query.ednsFlags and 0x8000);putShort(0)
    }.array()
    internal fun validEdnsOptions(bytes:ByteArray,start:Int,end:Int):Boolean {
        var p=start
        while(p<end) {
            if(p+4>end) return false
            val size=u16(bytes,p+2)
            p+=4
            if(p+size>end) return false
            p+=size
        }
        return p==end
    }
    private fun u16(bytes:ByteArray,p:Int)=((bytes[p].toInt() and 255) shl 8) or (bytes[p+1].toInt() and 255)

    fun wrapDnsResponseInIpUdp(query: DnsQuery, dnsBytes: ByteArray, dnsLength: Int): ByteArray {
        require(dnsLength >= 0 && dnsLength <= dnsBytes.size)
        require(dnsLength <= maxDnsPayloadLength(query))
        val udpLength = 8 + dnsLength
        require(udpLength <= 0xffff)

        val ipHeaderLength = if (query.isIpv6) 40 else 20
        val packet = ByteBuffer.allocate(ipHeaderLength + udpLength).order(ByteOrder.BIG_ENDIAN)
        if (query.isIpv6) {
            require(query.srcIp.size == 16 && query.dstIp.size == 16)
            packet.putInt(0x60000000)
            packet.putShort(udpLength.toShort())
            packet.put(17.toByte())
            packet.put(64.toByte())
            packet.put(query.dstIp)
            packet.put(query.srcIp)
        } else {
            require(query.srcIp.size == 4 && query.dstIp.size == 4)
            packet.put(0x45.toByte())
            packet.put(0)
            packet.putShort((20 + udpLength).toShort())
            packet.putShort(0)
            packet.putShort(0x4000.toShort())
            packet.put(64)
            packet.put(17)
            packet.putShort(0)
            packet.put(query.dstIp)
            packet.put(query.srcIp)
            packet.putShort(10, InternetChecksum.ipv4Header(packet.array(), 0, 20))
        }

        packet.position(ipHeaderLength)
        packet.putShort(query.dstPort.toShort())
        packet.putShort(query.srcPort.toShort())
        packet.putShort(udpLength.toShort())
        packet.putShort(0)
        packet.put(dnsBytes, 0, dnsLength)

        if (query.isIpv6) {
            val checksum = InternetChecksum.udpIpv6(
                source = query.dstIp,
                destination = query.srcIp,
                udpSegment = packet.array(),
                offset = 40,
                length = udpLength
            )
            packet.putShort(46, checksum)
        }
        return packet.array()
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

}
