package com.unblocker.app.logic.dns

import com.unblocker.app.logic.DomainName
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parsed DNS query retaining its caller-owned packet storage.
 *
 * Copy the DNS range before asynchronous use; equality intentionally compares only transaction ID and domain.
 *
 * @property transactionId DNS transaction-ID wire bits.
 * @property domain Canonical queried hostname.
 * @property queryType DNS QTYPE wire bits.
 * @property queryClass DNS QCLASS wire bits.
 * @property rawPacket Backing IP packet or DNS body; not defensively copied.
 * @property dnsOffset Zero-based byte offset of the DNS body in rawPacket.
 * @property dnsLength DNS body length in bytes.
 * @property srcIp Client address bytes, four for IPv4 or sixteen for IPv6.
 * @property dstIp Resolver destination address bytes; wire-only parsing supplies a compatibility placeholder.
 * @property srcPort Client UDP port, or a placeholder for wire-only queries.
 * @property dstPort DNS destination port.
 * @property isIpv6 Whether the client uses IPv6.
 * @property udpPayloadLimit Client DNS UDP payload limit in bytes.
 * @property hasEdns Whether the parsed UDP query included an OPT record.
 * @property ednsFlags Parsed EDNS flag bits.
 * @property cacheVariant Query flag/option discriminator for cache identity.
 * @property cacheAllowed Whether the parsed options permit cache reuse.
 */
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
    val cacheAllowed: Boolean = true,
) {
    /**
     * Compares only transaction ID and canonical domain, excluding transport and packet bytes.
     *
     * @param other Object to compare.
     * @return Whether other is a DnsQuery with the same transaction ID and domain.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DnsQuery
        return transactionId == other.transactionId && domain == other.domain
    }

    /**
     * Returns the hash of transaction ID and domain, matching the restricted equality contract.
     */
    override fun hashCode(): Int {
        var result = transactionId.toInt()
        result = 31 * result + domain.hashCode()
        return result
    }
}

/**
 * Owned DNS body for asynchronous forwarding after the TUN buffer is reused.
 *
 * @property query Query copy whose rawPacket is the isolated DNS body.
 * @property dnsPayload Isolated DNS body, shared with query.rawPacket.
 */
data class ForwardingDnsRequest(
    val query: DnsQuery,
    val dnsPayload: ByteArray,
)

/**
 * Copies this query DNS range and points a query copy at the isolated body.
 *
 * @return Forwarding request independent of the reusable original packet buffer.
 * @throws IndexOutOfBoundsException If the declared DNS end exceeds the backing array.
 */
fun DnsQuery.toForwardingRequest(): ForwardingDnsRequest {
    val payload = rawPacket.copyOfRange(dnsOffset, dnsOffset + dnsLength)
    val isolatedQuery = this.copy(rawPacket = payload, dnsOffset = 0, dnsLength = payload.size)
    return ForwardingDnsRequest(isolatedQuery, payload)
}

/**
 * Parses standard DNS queries and builds local IPv4/IPv6 UDP DNS responses.
 */
object DnsPacketUtil {
    /**
     * DNS A record type wire value.
     */
    const val TYPE_A: Short = 1

    /**
     * DNS AAAA record type wire value.
     */
    const val TYPE_AAAA: Short = 28

    /**
     * DNS Internet class wire value.
     */
    const val CLASS_IN: Short = 1

    /**
     * Computes the largest DNS body representable in the selected IP/UDP envelope.
     *
     * @param query Query determining IPv4 versus IPv6 overhead.
     * @return Maximum DNS body length in bytes, excluding IP and UDP headers.
     */
    fun maxDnsPayloadLength(query: DnsQuery): Int = if (query.isIpv6) 0xffff - 8 else 0xffff - 20 - 8

    /**
     * Reads the minimum positive answer TTL from a structurally valid successful response.
     *
     * @param payload DNS response body.
     * @param length Available body length in bytes.
     * @return Minimum answer TTL in seconds, or null for invalid, truncated, unsuccessful or zero-TTL responses.
     */
    fun minCacheTtlSeconds(
        payload: ByteArray,
        length: Int = payload.size,
    ): Long? =
        DnsResponseValidator
            .parseMetadata(payload, length)
            ?.takeIf { !it.truncated && it.rcode == 0 }
            ?.minPositiveTtlSeconds

    /**
     * Parses a single unfragmented standard DNS query over IPv4 or supported IPv6 UDP envelopes.
     *
     * @param packet Reusable TUN buffer, retained by the returned query without copying.
     * @param length Number of available IP packet bytes.
     * @return Parsed query, or null for unsupported transport, malformed bounds or invalid DNS syntax.
     */
    fun parseIpPacket(
        packet: ByteArray,
        length: Int,
    ): DnsQuery? {
        if (length < 28 || length > packet.size) return null

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        val isIpv6 = (version == 6)
        if (version != 4 && version != 6) return null

        val (udpOffset, srcIp, dstIp) =
            if (isIpv6) {
                if (length < 48) return null // 40 IPv6 + 8 UDP
                if (u16(packet, 4) == 0 || 40 + u16(packet, 4) != length) return null
                var nextHeader = packet[6].toInt() and 0xFF
                var offset = 40
                var hops = 0
                while (nextHeader != 17) {
                    if (hops++ >= 8 || offset + 8 > length) return null
                    val extensionLength =
                        when (nextHeader) {
                            0, 43, 60 -> {
                                if (nextHeader == 0 && offset != 40) return null
                                ((packet[offset + 1].toInt() and 255) + 1) * 8
                            }

                            44 -> {
                                if (packet[offset + 1].toInt() != 0 || u16(packet, offset + 2) != 0) return null
                                8 // Atomic fragments only; no reassembly or fragmented transport.
                            }

                            else -> {
                                return null
                            }
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
                if (u16(packet, 2) != length || u16(packet, 6) and 0xbfff != 0) return null
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
        if (qdCount != 1 || u16(packet, dnsOffset + 6) != 0 || u16(packet, dnsOffset + 8) != 0) return null

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
        val additional = u16(packet, dnsOffset + 10)
        var hasEdns = false
        var udpLimit = 512
        var ednsFlags = 0
        var optionKey = "none"
        var cacheAllowed = true
        if (additional == 1) {
            if (pos + 11 > dnsEnd || packet[pos].toInt() != 0 || u16(packet, pos + 1) != 41 ||
                packet[pos + 5].toInt() != 0 || packet[pos + 6].toInt() != 0
            ) {
                return null
            }
            val dataSize = u16(packet, pos + 9)
            if (pos + 11 + dataSize != dnsEnd || !validEdnsOptions(packet, pos + 11, dnsEnd)) return null
            hasEdns = true
            udpLimit = u16(packet, pos + 3).coerceAtLeast(512)
            ednsFlags = u16(packet, pos + 7)
            cacheAllowed = dataSize <= 256
            optionKey =
                if (cacheAllowed) {
                    "edns:$ednsFlags:" +
                        (pos + 11 until dnsEnd).joinToString("") {
                            val byte = packet[it].toInt() and 255
                            "0123456789abcdef"[byte ushr 4].toString() + "0123456789abcdef"[byte and 15]
                        }
                } else {
                    "uncacheable"
                }
            pos = dnsEnd
        } else if (additional != 0) {
            return null
        }
        if (pos != dnsEnd) return null

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
            cacheAllowed = cacheAllowed,
        )
    }

    /**
     * Builds a local IPv4/IPv6 UDP block response with zero A/AAAA answers or canary NXDOMAIN.
     *
     * @param query Parsed query supplying client addresses, ports, ID and question.
     * @return Complete reply packet; non-address QTYPEs receive an empty successful answer.
     * @throws IllegalArgumentException If query address sizes or response envelope lengths are invalid.
     */
    fun buildBlockedDnsResponsePacket(query: DnsQuery): ByteArray {
        val isDoHCanary = query.domain == "use-application-dns.net"
        val isA = query.queryType == TYPE_A
        val isAaaa = query.queryType == TYPE_AAAA

        // DNS Response Body
        val questionBytes = extractQuestionSection(query.rawPacket, query.dnsOffset, query.dnsLength)
        val answerRecordLength =
            if (isDoHCanary) {
                0
            } else if (isA) {
                16
            } else if (isAaaa) {
                28
            } else {
                0
            }

        val opt = if (query.hasEdns) minimalOpt(query) else byteArrayOf()
        val dnsResponseSize = 12 + questionBytes.size + answerRecordLength + opt.size
        val dnsFlags = if (isDoHCanary) 0x8583.toShort() else 0x8580.toShort() // NXDOMAIN (3) or NoError (0)
        val dnsResponse = ByteBuffer.allocate(dnsResponseSize).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(query.transactionId)
        dnsResponse.putShort(dnsFlags)
        dnsResponse.putShort(1.toShort())
        dnsResponse.putShort((if (answerRecordLength > 0) 1 else 0).toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort((if (query.hasEdns) 1 else 0).toShort())
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

    /**
     * Builds a local SERVFAIL response with the original question and optional minimal OPT.
     *
     * @param query Parsed query supplying client addressing and DNS identity.
     * @return Complete IPv4/IPv6 UDP reply packet.
     * @throws IllegalArgumentException If query address sizes or response envelope lengths are invalid.
     */
    fun buildServfailDnsResponsePacket(query: DnsQuery): ByteArray {
        val questionBytes = extractQuestionSection(query.rawPacket, query.dnsOffset, query.dnsLength)
        val opt = if (query.hasEdns) minimalOpt(query) else byteArrayOf()
        val dnsResponseSize = 12 + questionBytes.size + opt.size
        val dnsFlags = 0x8182.toShort()

        val dnsResponse = ByteBuffer.allocate(dnsResponseSize).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(query.transactionId)
        dnsResponse.putShort(dnsFlags)
        dnsResponse.putShort(1.toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort((if (query.hasEdns) 1 else 0).toShort())
        dnsResponse.put(questionBytes)
        dnsResponse.put(opt)
        return wrapDnsResponseInIpUdp(query, dnsResponse.array(), dnsResponseSize)
    }

    /**
     * Builds a DNS-only SERVFAIL response from an uncompressed single-question body.
     *
     * @param dnsWire DNS query bytes without a TCP length prefix.
     * @return Response body, or null when the header/question cannot be extracted.
     */
    fun buildServfailWireResponse(dnsWire: ByteArray): ByteArray? {
        if (dnsWire.size < 12) return null
        val txId = ((dnsWire[0].toInt() and 0xFF) shl 8 or (dnsWire[1].toInt() and 0xFF)).toShort()
        val qdCount = ((dnsWire[4].toInt() and 0xFF) shl 8 or (dnsWire[5].toInt() and 0xFF))
        if (qdCount != 1) return null

        var pos = 12
        while (pos < dnsWire.size) {
            val labelLen = dnsWire[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if (labelLen > 63 || pos + labelLen > dnsWire.size) return null
            pos += labelLen
        }
        if (pos + 4 > dnsWire.size) return null
        pos += 4
        val questionBytes = dnsWire.copyOfRange(12, pos)

        val dnsResponseSize = 12 + questionBytes.size
        val dnsFlags = 0x8182.toShort()

        val dnsResponse = ByteBuffer.allocate(dnsResponseSize).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(txId)
        dnsResponse.putShort(dnsFlags)
        dnsResponse.putShort(1.toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.put(questionBytes)
        return dnsResponse.array()
    }

    /**
     * Builds a DNS-only block response using zero A/AAAA answers or canary NXDOMAIN.
     *
     * @param dnsWire DNS query bytes without a TCP length prefix.
     * @return Response body, or null when its single question or hostname is invalid.
     */
    fun buildBlockedWireResponse(dnsWire: ByteArray): ByteArray? {
        if (dnsWire.size < 12) return null
        val txId = ((dnsWire[0].toInt() and 0xFF) shl 8 or (dnsWire[1].toInt() and 0xFF)).toShort()
        val qdCount = ((dnsWire[4].toInt() and 0xFF) shl 8 or (dnsWire[5].toInt() and 0xFF))
        if (qdCount != 1) return null

        var pos = 12
        val domainBuilder = StringBuilder()
        while (pos < dnsWire.size) {
            val labelLen = dnsWire[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if (labelLen > 63 || pos + labelLen > dnsWire.size) return null
            val label = DomainName.canonicalWireLabel(dnsWire, pos, labelLen) ?: return null
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            domainBuilder.append(label)
            pos += labelLen
        }
        if (pos + 4 > dnsWire.size) return null
        val qType = ((dnsWire[pos].toInt() and 0xFF) shl 8 or (dnsWire[pos + 1].toInt() and 0xFF)).toShort()
        pos += 4
        val questionBytes = dnsWire.copyOfRange(12, pos)

        val domain = DomainName.normalize(domainBuilder.toString()) ?: return null
        val isDoHCanary = domain == "use-application-dns.net"
        val isA = qType == TYPE_A
        val isAaaa = qType == TYPE_AAAA

        val answerRecordLength =
            if (isDoHCanary) {
                0
            } else if (isA) {
                16
            } else if (isAaaa) {
                28
            } else {
                0
            }
        val dnsResponseSize = 12 + questionBytes.size + answerRecordLength
        val dnsFlags = if (isDoHCanary) 0x8583.toShort() else 0x8580.toShort()

        val dnsResponse = ByteBuffer.allocate(dnsResponseSize).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(txId)
        dnsResponse.putShort(dnsFlags)
        dnsResponse.putShort(1.toShort())
        dnsResponse.putShort((if (answerRecordLength > 0) 1 else 0).toShort())
        dnsResponse.putShort(0.toShort())
        dnsResponse.putShort(0.toShort())
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
        return dnsResponse.array()
    }

    /**
     * Parses a DNS-only standard single question for TCP forwarding.
     *
     * This path retains the full body but does not parse additional records or EDNS negotiation.
     *
     * @param dnsWire DNS query body without its TCP length prefix; retained without copying.
     * @param isIpv6 Whether the client TCP connection uses IPv6.
     * @param clientIp Client address bytes retained in the query.
     * @return Query with compatibility transport placeholders, or null when question parsing fails.
     */
    fun parseWireQuery(
        dnsWire: ByteArray,
        isIpv6: Boolean,
        clientIp: ByteArray,
    ): DnsQuery? {
        if (dnsWire.size < 12) return null
        val txId = ((dnsWire[0].toInt() and 0xFF) shl 8 or (dnsWire[1].toInt() and 0xFF)).toShort()
        val flags = ((dnsWire[2].toInt() and 0xFF) shl 8 or (dnsWire[3].toInt() and 0xFF))
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery || flags and 0x7800 != 0) return null

        val qdCount = ((dnsWire[4].toInt() and 0xFF) shl 8 or (dnsWire[5].toInt() and 0xFF))
        if (qdCount != 1) return null

        var pos = 12
        val domainBuilder = StringBuilder()
        while (pos < dnsWire.size) {
            val labelLen = dnsWire[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if (labelLen > 63 || pos + labelLen > dnsWire.size) return null
            val label = DomainName.canonicalWireLabel(dnsWire, pos, labelLen) ?: return null
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            domainBuilder.append(label)
            pos += labelLen
        }
        if (pos + 4 > dnsWire.size) return null
        val qType = ((dnsWire[pos].toInt() and 0xFF) shl 8 or (dnsWire[pos + 1].toInt() and 0xFF)).toShort()
        val qClass = ((dnsWire[pos + 2].toInt() and 0xFF) shl 8 or (dnsWire[pos + 3].toInt() and 0xFF)).toShort()
        pos += 4

        val domain = DomainName.normalize(domainBuilder.toString()) ?: return null
        val dstIp =
            if (isIpv6) {
                byteArrayOf(
                    0xfd.toByte(),
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                )
            } else {
                byteArrayOf(10, 10, 0, 1)
            }

        return DnsQuery(
            transactionId = txId,
            domain = domain,
            queryType = qType,
            queryClass = qClass,
            rawPacket = dnsWire,
            dnsOffset = 0,
            dnsLength = dnsWire.size,
            srcIp = clientIp,
            dstIp = dstIp,
            srcPort = 53,
            dstPort = 53,
            isIpv6 = isIpv6,
            hasEdns = false,
            udpPayloadLimit = 65535,
            ednsFlags = 0,
            cacheVariant = "$flags:tcp",
            cacheAllowed = true,
        )
    }

    /**
     * Restores the client ID and negotiates a fitting response or synthetic truncated reply.
     *
     * @param query Parsed UDP query and its EDNS payload limit.
     * @param bytes Validated DNS response body or cached copy.
     * @return Complete IPv4/IPv6 UDP reply packet.
     * @throws IllegalArgumentException If response envelope lengths or query address sizes are invalid.
     */
    fun wrapClientResponse(
        query: DnsQuery,
        bytes: ByteArray,
    ): ByteArray {
        val limit = minOf(query.udpPayloadLimit.coerceAtLeast(512), maxDnsPayloadLength(query))
        if (bytes.size <= limit) {
            val txBytes =
                if (bytes.size >= 2 &&
                    (
                        bytes[0] != (query.transactionId.toInt() ushr 8).toByte() ||
                            bytes[1] != query.transactionId.toByte()
                    )
                ) {
                    val cloned = bytes.clone()
                    cloned[0] = (query.transactionId.toInt() ushr 8).toByte()
                    cloned[1] = query.transactionId.toByte()
                    cloned
                } else {
                    bytes
                }
            return wrapDnsResponseInIpUdp(query, txBytes, txBytes.size)
        }
        require(bytes.size >= 12)
        val question = extractQuestionSection(query.rawPacket, query.dnsOffset, query.dnsLength)
        val opt = if (query.hasEdns) minimalOpt(query) else byteArrayOf()
        val truncated = ByteBuffer.allocate(12 + question.size + opt.size).order(ByteOrder.BIG_ENDIAN)
        truncated.putShort(query.transactionId)
        truncated.putShort(((u16(bytes, 2) or 0x8200) and 0xffdf).toShort()) // Clear AD on synthetic TC.
        truncated.putShort(1)
        truncated.putShort(0)
        truncated.putShort(0)
        truncated.putShort((if (query.hasEdns) 1 else 0).toShort())
        truncated.put(question)
        truncated.put(opt)
        return wrapDnsResponseInIpUdp(query, truncated.array(), truncated.capacity())
    }

    private fun minimalOpt(query: DnsQuery): ByteArray =
        ByteBuffer
            .allocate(11)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(0)
                putShort(41)
                putShort(minOf(query.udpPayloadLimit, maxDnsPayloadLength(query)).toShort())
                putInt(query.ednsFlags and 0x8000)
                putShort(0)
            }.array()

    internal fun validEdnsOptions(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Boolean {
        var p = start
        while (p < end) {
            if (p + 4 > end) return false
            val size = u16(bytes, p + 2)
            p += 4
            if (p + size > end) return false
            p += size
        }
        return p == end
    }

    private fun u16(
        bytes: ByteArray,
        p: Int,
    ) = ((bytes[p].toInt() and 255) shl 8) or (bytes[p + 1].toInt() and 255)

    /**
     * Wraps DNS bytes in a reply envelope with swapped client/resolver addresses and ports.
     *
     * @param query Query supplying addresses, ports and IP version.
     * @param dnsBytes DNS response body.
     * @param dnsLength Number of body bytes to include from offset zero.
     * @return Complete packet with IPv4 header checksum or IPv6 UDP checksum.
     * @throws IllegalArgumentException If lengths exceed protocol bounds or address sizes mismatch the IP version.
     */
    fun wrapDnsResponseInIpUdp(
        query: DnsQuery,
        dnsBytes: ByteArray,
        dnsLength: Int,
    ): ByteArray {
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
            val checksum =
                InternetChecksum.udpIpv6(
                    source = query.dstIp,
                    destination = query.srcIp,
                    udpSegment = packet.array(),
                    offset = 40,
                    length = udpLength,
                )
            packet.putShort(46, checksum)
        }
        return packet.array()
    }

    private fun extractQuestionSection(
        packet: ByteArray,
        dnsOffset: Int,
        dnsLength: Int,
    ): ByteArray {
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

    private var lastErrorResetTime = 0L
    private var errorPacketCount = 0
    private const val MAX_ERROR_PACKETS_PER_SEC = 50

    /**
     * Builds a rate-limited FORMERR or NOTIMP response for recognizable UDP DNS traffic.
     *
     * @param packet TUN packet storage; length must not exceed its size.
     * @param length Available IP packet bytes.
     * @return Complete reply packet, or null when inapplicable, too short or rate-limited.
     * @throws IndexOutOfBoundsException If supplied bounds are inconsistent with packet storage.
     */
    fun buildErrorResponseIfApplicable(
        packet: ByteArray,
        length: Int,
    ): ByteArray? {
        if (length < 28) return null
        val isIpv6 = (packet[0].toInt() and 0xF0) == 0x60
        val (udpOffset, srcIp, dstIp) =
            if (isIpv6) {
                if (length < 48) return null
                var offset = 40
                var nextHeader = packet[6].toInt() and 255
                while (nextHeader in setOf(0, 43, 60, 44)) {
                    if (offset + 2 > length) return null
                    val extLen =
                        when (nextHeader) {
                            44 -> 8
                            else -> ((packet[offset + 1].toInt() and 255) + 1) * 8
                        }
                    if (offset + extLen + 8 > length) return null
                    nextHeader = packet[offset].toInt() and 255
                    offset += extLen
                }
                if (nextHeader != 17) return null
                val src = ByteArray(16) { packet[8 + it] }
                val dst = ByteArray(16) { packet[24 + it] }
                Triple(offset, src, dst)
            } else {
                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (ihl < 20 || length < ihl + 8) return null
                if (packet[9].toInt() and 0xFF != 17) return null
                val src = ByteArray(4) { packet[12 + it] }
                val dst = ByteArray(4) { packet[16 + it] }
                Triple(ihl, src, dst)
            }

        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsOffset = udpOffset + 8
        val dnsLength = udpLength - 8
        if (udpLength < 20 || dnsLength < 12 || dnsOffset + dnsLength > length) return null

        val txId = ((packet[dnsOffset].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 1].toInt() and 0xFF)).toShort()
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8 or (packet[dnsOffset + 3].toInt() and 0xFF))
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val opcode = (flags and 0x7800) shr 11
        val rcode = if (opcode != 0) 4 else 1

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastErrorResetTime >= 1000L) {
                lastErrorResetTime = now
                errorPacketCount = 0
            }
            if (errorPacketCount >= MAX_ERROR_PACKETS_PER_SEC) {
                return null
            }
            errorPacketCount++
        }

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val responseFlags = 0x8000 or (flags and 0x7800) or (flags and 0x0100) or rcode

        val dnsResponse = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        dnsResponse.putShort(txId)
        dnsResponse.putShort(responseFlags.toShort())
        dnsResponse.putShort(0)
        dnsResponse.putShort(0)
        dnsResponse.putShort(0)
        dnsResponse.putShort(0)

        val dnsBytes = dnsResponse.array()
        val respUdpLength = 8 + 12
        val ipHeaderLength = if (isIpv6) 40 else 20
        val packetOut = ByteBuffer.allocate(ipHeaderLength + respUdpLength).order(ByteOrder.BIG_ENDIAN)

        if (isIpv6) {
            packetOut.putInt(0x60000000)
            packetOut.putShort(respUdpLength.toShort())
            packetOut.put(17.toByte())
            packetOut.put(64.toByte())
            packetOut.put(dstIp)
            packetOut.put(srcIp)
        } else {
            packetOut.put(0x45.toByte())
            packetOut.put(0)
            packetOut.putShort((20 + respUdpLength).toShort())
            packetOut.putShort(0)
            packetOut.putShort(0x4000.toShort())
            packetOut.put(64)
            packetOut.put(17)
            packetOut.putShort(0)
            packetOut.put(dstIp)
            packetOut.put(srcIp)
            packetOut.putShort(10, InternetChecksum.ipv4Header(packetOut.array(), 0, 20))
        }

        packetOut.position(ipHeaderLength)
        packetOut.putShort(53.toShort())
        packetOut.putShort(srcPort.toShort())
        packetOut.putShort(respUdpLength.toShort())
        packetOut.putShort(0)
        packetOut.put(dnsBytes)

        if (isIpv6) {
            val checksum =
                InternetChecksum.udpIpv6(
                    source = dstIp,
                    destination = srcIp,
                    udpSegment = packetOut.array(),
                    offset = 40,
                    length = respUdpLength,
                )
            packetOut.putShort(46, checksum)
        }

        return packetOut.array()
    }
}
