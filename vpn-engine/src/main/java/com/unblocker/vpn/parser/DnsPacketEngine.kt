package com.unblocker.vpn.parser

import java.util.Locale

/**
 * Parses a subset of outgoing IPv4/IPv6 UDP DNS packets and builds reverse-direction responses.
 *
 * The engine rejects compressed query names and IPv6 extension-header chains. Parsing validates
 * selected lengths and flags, not every IP/DNS invariant or question field. Callers own packet
 * storage and must provide bounds consistent with the array.
 */
object DnsPacketEngine {
    /**
     * Standard DNS UDP destination port.
     */
    const val DNS_PORT = 53

    /**
     * Minimum IPv4 header length in bytes, without options.
     */
    const val IPV4_HEADER_MIN_LEN = 20

    /**
     * Fixed IPv6 header length in bytes, excluding extension headers.
     */
    const val IPV6_HEADER_LEN = 40

    /**
     * UDP header length in bytes.
     */
    const val UDP_HEADER_LEN = 8

    /**
     * DNS fixed header length in bytes.
     */
    const val DNS_HEADER_LEN = 12

    private const val UDP_PROTOCOL_NUMBER = 17
    private const val MAX_DOMAIN_LENGTH = 253

    /**
     * Parsed query metadata with byte offsets into the original IP packet.
     *
     * @property domain Lowercase decoded query name with trailing dots trimmed.
     * @property isIpv6 Whether the packet uses IPv6.
     * @property ipHeaderLength IP header length in bytes, including IPv4 options.
     * @property totalLength End of the declared UDP datagram, measured from the packet start in bytes.
     * @property udpOffset UDP header byte offset from the packet start.
     * @property dnsOffset DNS payload byte offset from the packet start.
     * @property srcPort Original UDP source port as an unsigned 16-bit value in an Int.
     * @property dstPort Original UDP destination port, required to be DNS_PORT by the parser.
     * @property txId DNS transaction ID bits represented as a signed Short.
     */
    data class ParsedDnsQuery(
        val domain: String,
        val isIpv6: Boolean,
        val ipHeaderLength: Int,
        val totalLength: Int,
        val udpOffset: Int,
        val dnsOffset: Int,
        val srcPort: Int,
        val dstPort: Int,
        val txId: Short,
    )

    /**
     * Extracts metadata from a direct UDP DNS query without IPv6 extension headers.
     *
     * Requires port 53, QR=0, at least one question, and decodable uncompressed name labels. It does
     * not validate checksums, DNS question type/class, all label characters, or all fragmentation rules.
     *
     * @param packet Raw IP packet storage, stable for the duration of the call.
     * @param length Declared valid prefix length in bytes; must not exceed [packet] size.
     * @return Parsed metadata, or null for rejected input.
     * @throws IndexOutOfBoundsException If declared bounds permit indexing beyond the supplied array.
     */
    fun parseOutgoingDnsPacket(
        packet: ByteArray,
        length: Int,
    ): ParsedDnsQuery? {
        if (length < IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

        val ipVersion = (packet[0].toInt() ushr 4) and 0x0F
        val isIpv6 = (ipVersion == 6)
        val ipHeaderLength: Int
        val totalPacketLength: Int

        when (ipVersion) {
            4 -> {
                val headerLengthWords = packet[0].toInt() and 0x0F
                ipHeaderLength = headerLengthWords * 4
                if (headerLengthWords < 5 || length < ipHeaderLength + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

                // Verify UDP protocol (17)
                val protocol = packet[9].toInt() and 0xFF
                if (protocol != UDP_PROTOCOL_NUMBER) return null

                val reportedTotal = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                totalPacketLength = minOf(reportedTotal, length)
            }

            6 -> {
                ipHeaderLength = IPV6_HEADER_LEN
                if (length < ipHeaderLength + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

                // Verify Next Header is UDP (17)
                val nextHeader = packet[6].toInt() and 0xFF
                if (nextHeader != UDP_PROTOCOL_NUMBER) return null

                val payloadLength = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                totalPacketLength = minOf(IPV6_HEADER_LEN + payloadLength, length)
            }

            else -> {
                return null
            }
        }

        val udpOffset = ipHeaderLength
        if (udpOffset + UDP_HEADER_LEN + DNS_HEADER_LEN > totalPacketLength) return null

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        // Only intercept DNS queries destined to port 53
        if (dstPort != DNS_PORT) return null

        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        if (udpLength < UDP_HEADER_LEN + DNS_HEADER_LEN || udpOffset + udpLength > totalPacketLength) return null

        val dnsOffset = udpOffset + UDP_HEADER_LEN
        val txId = (((packet[dnsOffset].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 1].toInt() and 0xFF)).toShort()
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 3].toInt() and 0xFF)

        // QR bit is MSB of flags (0x8000). 0 = Query, 1 = Response
        if ((flags and 0x8000) != 0) return null

        // QDCOUNT: Number of questions
        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 5].toInt() and 0xFF)
        if (qdCount <= 0) return null

        // Decode QNAME starting at dnsOffset + DNS_HEADER_LEN
        val qnameStart = dnsOffset + DNS_HEADER_LEN
        val udpEnd = udpOffset + udpLength
        val domain = decodeQName(packet, qnameStart, udpEnd) ?: return null

        return ParsedDnsQuery(
            domain = domain,
            isIpv6 = isIpv6,
            ipHeaderLength = ipHeaderLength,
            totalLength = udpOffset + udpLength,
            udpOffset = udpOffset,
            dnsOffset = dnsOffset,
            srcPort = srcPort,
            dstPort = dstPort,
            txId = txId,
        )
    }

    /**
     * Decodes uncompressed labels within the declared datagram bounds, rejecting compression pointers.
     */
    private fun decodeQName(
        packet: ByteArray,
        startOffset: Int,
        endLimit: Int,
    ): String? {
        val domainBuilder = StringBuilder(64)
        var position = startOffset

        while (position < endLimit) {
            val labelLength = packet[position].toInt() and 0xFF
            if (labelLength == 0) {
                position++
                break
            }

            // Reject pointer compression in incoming queries to prevent loops / evasion
            if ((labelLength and 0xC0) != 0) {
                return null
            }

            position++
            if (position + labelLength > endLimit) return null

            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            for (i in 0 until labelLength) {
                val characterCode = packet[position + i].toInt() and 0xFF
                // Preserve label bytes as characters; this decoder does not validate the character set.
                domainBuilder.append(characterCode.toChar())
            }
            position += labelLength

            if (domainBuilder.length > MAX_DOMAIN_LENGTH) return null
        }

        val domain = domainBuilder.toString().lowercase(Locale.ROOT).trimEnd('.')
        return if (domain.isNotEmpty()) domain else null
    }

    /**
     * Copies the query datagram, reverses addresses and ports, and sets NXDOMAIN response flags.
     *
     * Answer, authority, and additional counts are cleared; bytes after those header fields remain
     * from the original datagram. IPv4 header and IPv6 UDP checksums are recomputed, while IPv4 UDP
     * checksum is set to zero.
     *
     * @param packet Original IP packet associated with [query].
     * @param query Matching metadata previously returned by the parser.
     * @return New reverse-direction IP/UDP packet with DNS flags 0x8183.
     * @throws IndexOutOfBoundsException If supplied metadata does not address valid packet/header bytes.
     */
    fun synthesizeNxDomainResponse(
        packet: ByteArray,
        query: ParsedDnsQuery,
    ): ByteArray {
        val responsePacket = packet.copyOf(query.totalLength)

        // 1. Swap Source and Destination IP addresses
        if (query.isIpv6) {
            // IPv6 src: bytes 8..23, dst: bytes 24..39
            for (i in 0 until 16) {
                val addressByte = responsePacket[8 + i]
                responsePacket[8 + i] = responsePacket[24 + i]
                responsePacket[24 + i] = addressByte
            }
        } else {
            // IPv4 src: bytes 12..15, dst: bytes 16..19
            for (i in 0 until 4) {
                val addressByte = responsePacket[12 + i]
                responsePacket[12 + i] = responsePacket[16 + i]
                responsePacket[16 + i] = addressByte
            }
        }

        // 2. Swap Source and Destination UDP ports
        val udpOffset = query.udpOffset
        val srcPortByte0 = responsePacket[udpOffset]
        val srcPortByte1 = responsePacket[udpOffset + 1]
        responsePacket[udpOffset] = responsePacket[udpOffset + 2]
        responsePacket[udpOffset + 1] = responsePacket[udpOffset + 3]
        responsePacket[udpOffset + 2] = srcPortByte0
        responsePacket[udpOffset + 3] = srcPortByte1

        // 3. DNS Header: Set flags = 0x8183 (Response, Recursion Desired, Recursion Available, NXDOMAIN)
        val dnsOffset = query.dnsOffset
        responsePacket[dnsOffset + 2] = 0x81.toByte()
        responsePacket[dnsOffset + 3] = 0x83.toByte()

        // Zero out ANCOUNT (answers), NSCOUNT (authorities), ARCOUNT (additionals)
        responsePacket[dnsOffset + 6] = 0
        responsePacket[dnsOffset + 7] = 0
        responsePacket[dnsOffset + 8] = 0
        responsePacket[dnsOffset + 9] = 0
        responsePacket[dnsOffset + 10] = 0
        responsePacket[dnsOffset + 11] = 0

        // 4. Compute Checksums
        if (query.isIpv6) {
            val udpLength = query.totalLength - query.udpOffset
            responsePacket[udpOffset + 6] = 0
            responsePacket[udpOffset + 7] = 0
            val udpChecksum = calculateIpv6UdpChecksum(responsePacket, udpOffset, udpLength)
            responsePacket[udpOffset + 6] = ((udpChecksum.toInt() ushr 8) and 0xFF).toByte()
            responsePacket[udpOffset + 7] = (udpChecksum.toInt() and 0xFF).toByte()
        } else {
            // IPv4 header checksum
            responsePacket[10] = 0
            responsePacket[11] = 0
            val ipChecksum = calculateIpv4Checksum(responsePacket, 0, query.ipHeaderLength)
            responsePacket[10] = ((ipChecksum.toInt() ushr 8) and 0xFF).toByte()
            responsePacket[11] = (ipChecksum.toInt() and 0xFF).toByte()

            // UDP checksum is optional in IPv4 (0x0000 = no checksum)
            responsePacket[udpOffset + 6] = 0
            responsePacket[udpOffset + 7] = 0
        }

        return responsePacket
    }

    /**
     * Constructs a reverse-direction IP/UDP packet around a caller-validated DNS payload.
     *
     * @param originalPacket Original query packet supplying its IP header template.
     * @param query Matching query offsets and source/destination ports.
     * @param upstreamDnsPayload DNS response bytes; response matching must be checked by the caller.
     * @return New packet with swapped addresses/ports and updated lengths and checksums.
     * @throws IndexOutOfBoundsException If the template or metadata cannot provide the required header bytes.
     */
    fun buildForwardedDnsResponse(
        originalPacket: ByteArray,
        query: ParsedDnsQuery,
        upstreamDnsPayload: ByteArray,
    ): ByteArray {
        val udpLength = UDP_HEADER_LEN + upstreamDnsPayload.size
        val totalLength = query.ipHeaderLength + udpLength
        val responsePacket = ByteArray(totalLength)

        // 1. Copy and configure IP header
        if (query.isIpv6) {
            // Copy original IPv6 header template
            System.arraycopy(originalPacket, 0, responsePacket, 0, IPV6_HEADER_LEN)
            // Swap IPv6 src and dst
            for (i in 0 until 16) {
                responsePacket[8 + i] = originalPacket[24 + i]
                responsePacket[24 + i] = originalPacket[8 + i]
            }
            // Set IPv6 payload length (bytes 4..5)
            responsePacket[4] = ((udpLength ushr 8) and 0xFF).toByte()
            responsePacket[5] = (udpLength and 0xFF).toByte()
        } else {
            // Copy original IPv4 header template
            System.arraycopy(originalPacket, 0, responsePacket, 0, query.ipHeaderLength)
            // Swap IPv4 src and dst
            for (i in 0 until 4) {
                responsePacket[12 + i] = originalPacket[16 + i]
                responsePacket[16 + i] = originalPacket[12 + i]
            }
            // Set IPv4 total length (bytes 2..3)
            responsePacket[2] = ((totalLength ushr 8) and 0xFF).toByte()
            responsePacket[3] = (totalLength and 0xFF).toByte()
        }

        // 2. Configure UDP header
        val udpOffset = query.ipHeaderLength
        responsePacket[udpOffset] = ((query.dstPort ushr 8) and 0xFF).toByte()
        responsePacket[udpOffset + 1] = (query.dstPort and 0xFF).toByte()
        responsePacket[udpOffset + 2] = ((query.srcPort ushr 8) and 0xFF).toByte()
        responsePacket[udpOffset + 3] = (query.srcPort and 0xFF).toByte()
        responsePacket[udpOffset + 4] = ((udpLength ushr 8) and 0xFF).toByte()
        responsePacket[udpOffset + 5] = (udpLength and 0xFF).toByte()
        responsePacket[udpOffset + 6] = 0
        responsePacket[udpOffset + 7] = 0

        // 3. Copy DNS payload
        System.arraycopy(upstreamDnsPayload, 0, responsePacket, udpOffset + UDP_HEADER_LEN, upstreamDnsPayload.size)

        // 4. Calculate Checksums
        if (query.isIpv6) {
            val udpChecksum = calculateIpv6UdpChecksum(responsePacket, udpOffset, udpLength)
            responsePacket[udpOffset + 6] = ((udpChecksum.toInt() ushr 8) and 0xFF).toByte()
            responsePacket[udpOffset + 7] = (udpChecksum.toInt() and 0xFF).toByte()
        } else {
            responsePacket[10] = 0
            responsePacket[11] = 0
            val ipChecksum = calculateIpv4Checksum(responsePacket, 0, query.ipHeaderLength)
            responsePacket[10] = ((ipChecksum.toInt() ushr 8) and 0xFF).toByte()
            responsePacket[11] = (ipChecksum.toInt() and 0xFF).toByte()
        }

        return responsePacket
    }

    /**
     * Computes a 16-bit Internet checksum over the supplied byte range.
     *
     * Set an existing checksum field to zero before generating a replacement checksum.
     *
     * @param data Header storage to read without mutation.
     * @param offset First header byte index, expected to be nonnegative.
     * @param length Number of bytes to include; expected to stay within [data].
     * @return One's-complement checksum bits represented as a signed Short.
     * @throws IndexOutOfBoundsException If the supplied range indexes outside [data].
     */
    fun calculateIpv4Checksum(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((length and 1) != 0) {
            sum += (data[end - 1].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.toInt().inv() and 0xFFFF).toShort()
    }

    /**
     * Checks the folded Internet checksum sum including the stored checksum field.
     *
     * @param data Header storage including its existing checksum.
     * @param offset First header byte index, expected to be nonnegative.
     * @param length Number of bytes to include; expected to stay within [data].
     * @return Whether the folded sum equals 0xFFFF.
     * @throws IndexOutOfBoundsException If the supplied range indexes outside [data].
     */
    fun verifyIpv4Checksum(
        data: ByteArray,
        offset: Int = 0,
        length: Int,
    ): Boolean {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((length and 1) != 0) {
            sum += (data[end - 1].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum and 0xFFFFL) == 0xFFFFL
    }

    /**
     * Computes the IPv6 UDP checksum using addresses at the fixed IPv6 header offsets.
     *
     * The UDP checksum field is excluded from summation. A zero result is encoded as 0xFFFF.
     *
     * @param data Packet storage containing the IPv6 header, UDP header, and payload.
     * @param udpOffset Byte offset of the UDP header.
     * @param udpLength UDP header plus payload length in bytes, expected to fit the packet.
     * @return Mandatory UDP checksum bits represented as a signed Short.
     * @throws IndexOutOfBoundsException If the required address or UDP bytes lie outside [data].
     */
    fun calculateIpv6UdpChecksum(
        data: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Short {
        var sum = 0L

        // 1. IPv6 Pseudo-header: Source IPv6 (16 bytes: data[8..23])
        var i = 8
        while (i < 24) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }

        // 2. IPv6 Pseudo-header: Destination IPv6 (16 bytes: data[24..39])
        i = 24
        while (i < 40) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }

        // 3. IPv6 Pseudo-header: UDP Length (32-bit: 0x0000 + udpLength)
        sum += (udpLength and 0xFFFF)

        // 4. IPv6 Pseudo-header: Next Header (32-bit: 0x0000 + 17)
        sum += UDP_PROTOCOL_NUMBER.toLong()

        // 5. UDP Header & Data (checksum field at udpOffset + 6..7 is treated as 0)
        i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            if (i != udpOffset + 6) {
                sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            }
            i += 2
        }
        if ((udpLength and 1) != 0) {
            sum += (data[end - 1].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }

        var checksum = (sum.toInt().inv() and 0xFFFF)
        if (checksum == 0) checksum = 0xFFFF // RFC 2460: Zero checksum transmitted as 0xFFFF
        return checksum.toShort()
    }
}
