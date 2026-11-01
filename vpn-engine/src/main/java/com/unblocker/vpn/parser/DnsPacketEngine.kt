package com.unblocker.vpn.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * High-performance dual-stack DNS packet inspection, checksum synthesis,
 * and NXDOMAIN block generator.
 *
 * Implements strict RFC 768 (UDP), RFC 791 (IPv4), RFC 2460 / RFC 8200 (IPv6),
 * and RFC 1035 (DNS) wire formatting.
 */
object DnsPacketEngine {

    const val DNS_PORT = 53
    const val IPV4_HEADER_MIN_LEN = 20
    const val IPV6_HEADER_LEN = 40
    const val UDP_HEADER_LEN = 8
    const val DNS_HEADER_LEN = 12

    /**
     * Parsed DNS query metadata containing exact packet byte offsets.
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
        val txId: Short
    )

    /**
     * Inspects a raw IP packet and extracts outgoing DNS query metadata.
     *
     * Validates:
     * - IPv4 / IPv6 headers and boundaries
     * - UDP protocol (17)
     * - Destination port 53
     * - DNS query format (QR = 0, QDCOUNT > 0)
     * - RFC 1035 label boundaries without pointer compression loops
     *
     * @param packet Raw IP packet bytes.
     * @param length Number of valid bytes in [packet].
     * @return [ParsedDnsQuery] if valid, or null if non-DNS or malformed.
     */
    fun parseOutgoingDnsPacket(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

        val ipVersion = (packet[0].toInt() ushr 4) and 0x0F
        val isIpv6 = (ipVersion == 6)
        val ipHeaderLength: Int
        val totalPacketLength: Int

        when (ipVersion) {
            4 -> {
                val ihl = packet[0].toInt() and 0x0F
                ipHeaderLength = ihl * 4
                if (ihl < 5 || length < ipHeaderLength + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

                // Verify UDP protocol (17)
                val protocol = packet[9].toInt() and 0xFF
                if (protocol != 17) return null

                val reportedTotal = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                totalPacketLength = minOf(reportedTotal, length)
            }
            6 -> {
                ipHeaderLength = IPV6_HEADER_LEN
                if (length < ipHeaderLength + UDP_HEADER_LEN + DNS_HEADER_LEN) return null

                // Verify Next Header is UDP (17)
                val nextHeader = packet[6].toInt() and 0xFF
                if (nextHeader != 17) return null

                val payloadLength = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                totalPacketLength = minOf(IPV6_HEADER_LEN + payloadLength, length)
            }
            else -> return null
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

        // Decode QNAME starting at dnsOffset + 12
        val qnameStart = dnsOffset + 12
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
            txId = txId
        )
    }

    /**
     * Decodes DNS wire-format labels into a lowercase domain string with strict bounds
     * and compression pointer loop protection.
     */
    private fun decodeQName(packet: ByteArray, startOffset: Int, endLimit: Int): String? {
        val sb = StringBuilder(64)
        var pos = startOffset

        while (pos < endLimit) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++
                break
            }

            // Reject pointer compression in incoming queries to prevent loops / evasion
            if ((labelLen and 0xC0) != 0) {
                return null
            }

            pos++
            if (pos + labelLen > endLimit) return null

            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until labelLen) {
                val c = packet[pos + i].toInt() and 0xFF
                // Allowed DNS ASCII characters: a-z, 0-9, -, _, .
                sb.append(c.toChar())
            }
            pos += labelLen

            if (sb.length > 253) return null
        }

        val domain = sb.toString().lowercase(Locale.ROOT).trimEnd('.')
        return if (domain.isNotEmpty()) domain else null
    }

    /**
     * Synthesizes an immediate NXDOMAIN (RCODE=3) DNS response packet with valid IP/UDP
     * checksums to instantly sinkhole blocked domains.
     *
     * Swaps source/dest IPs and ports, sets DNS flags to 0x8183 (QR=1, RD=1, RA=1, NXDOMAIN=3),
     * zeroes answer/authority/additional counts, and computes proper RFC 791 / RFC 2460 checksums.
     */
    fun synthesizeNxDomainResponse(packet: ByteArray, query: ParsedDnsQuery): ByteArray {
        val resp = packet.copyOf(query.totalLength)

        // 1. Swap Source and Destination IP addresses
        if (query.isIpv6) {
            // IPv6 src: bytes 8..23, dst: bytes 24..39
            for (i in 0 until 16) {
                val temp = resp[8 + i]
                resp[8 + i] = resp[24 + i]
                resp[24 + i] = temp
            }
        } else {
            // IPv4 src: bytes 12..15, dst: bytes 16..19
            for (i in 0 until 4) {
                val temp = resp[12 + i]
                resp[12 + i] = resp[16 + i]
                resp[16 + i] = temp
            }
        }

        // 2. Swap Source and Destination UDP ports
        val u = query.udpOffset
        val srcPortByte0 = resp[u]
        val srcPortByte1 = resp[u + 1]
        resp[u] = resp[u + 2]
        resp[u + 1] = resp[u + 3]
        resp[u + 2] = srcPortByte0
        resp[u + 3] = srcPortByte1

        // 3. DNS Header: Set flags = 0x8183 (Response, Recursion Desired, Recursion Available, NXDOMAIN)
        val d = query.dnsOffset
        resp[d + 2] = 0x81.toByte()
        resp[d + 3] = 0x83.toByte()

        // Zero out ANCOUNT (answers), NSCOUNT (authorities), ARCOUNT (additionals)
        resp[d + 6] = 0; resp[d + 7] = 0
        resp[d + 8] = 0; resp[d + 9] = 0
        resp[d + 10] = 0; resp[d + 11] = 0

        // 4. Compute Checksums
        if (query.isIpv6) {
            val udpLen = query.totalLength - query.udpOffset
            resp[u + 6] = 0
            resp[u + 7] = 0
            val udpCksum = calculateIpv6UdpChecksum(resp, u, udpLen)
            resp[u + 6] = ((udpCksum.toInt() ushr 8) and 0xFF).toByte()
            resp[u + 7] = (udpCksum.toInt() and 0xFF).toByte()
        } else {
            // IPv4 header checksum
            resp[10] = 0
            resp[11] = 0
            val ipCksum = calculateIpv4Checksum(resp, 0, query.ipHeaderLength)
            resp[10] = ((ipCksum.toInt() ushr 8) and 0xFF).toByte()
            resp[11] = (ipCksum.toInt() and 0xFF).toByte()

            // UDP checksum is optional in IPv4 (0x0000 = no checksum)
            resp[u + 6] = 0
            resp[u + 7] = 0
        }

        return resp
    }

    /**
     * Constructs a valid IP+UDP response packet wrapping raw upstream DNS response bytes.
     */
    fun buildForwardedDnsResponse(
        originalPacket: ByteArray,
        query: ParsedDnsQuery,
        upstreamDnsPayload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER_LEN + upstreamDnsPayload.size
        val totalLength = query.ipHeaderLength + udpLength
        val resp = ByteArray(totalLength)

        // 1. Copy and configure IP header
        if (query.isIpv6) {
            // Copy original IPv6 header template
            System.arraycopy(originalPacket, 0, resp, 0, IPV6_HEADER_LEN)
            // Swap IPv6 src and dst
            for (i in 0 until 16) {
                resp[8 + i] = originalPacket[24 + i]
                resp[24 + i] = originalPacket[8 + i]
            }
            // Set IPv6 payload length (bytes 4..5)
            resp[4] = ((udpLength ushr 8) and 0xFF).toByte()
            resp[5] = (udpLength and 0xFF).toByte()
        } else {
            // Copy original IPv4 header template
            System.arraycopy(originalPacket, 0, resp, 0, query.ipHeaderLength)
            // Swap IPv4 src and dst
            for (i in 0 until 4) {
                resp[12 + i] = originalPacket[16 + i]
                resp[16 + i] = originalPacket[12 + i]
            }
            // Set IPv4 total length (bytes 2..3)
            resp[2] = ((totalLength ushr 8) and 0xFF).toByte()
            resp[3] = (totalLength and 0xFF).toByte()
        }

        // 2. Configure UDP header
        val u = query.ipHeaderLength
        resp[u] = ((query.dstPort ushr 8) and 0xFF).toByte()
        resp[u + 1] = (query.dstPort and 0xFF).toByte()
        resp[u + 2] = ((query.srcPort ushr 8) and 0xFF).toByte()
        resp[u + 3] = (query.srcPort and 0xFF).toByte()
        resp[u + 4] = ((udpLength ushr 8) and 0xFF).toByte()
        resp[u + 5] = (udpLength and 0xFF).toByte()
        resp[u + 6] = 0
        resp[u + 7] = 0

        // 3. Copy DNS payload
        System.arraycopy(upstreamDnsPayload, 0, resp, u + UDP_HEADER_LEN, upstreamDnsPayload.size)

        // 4. Calculate Checksums
        if (query.isIpv6) {
            val udpCksum = calculateIpv6UdpChecksum(resp, u, udpLength)
            resp[u + 6] = ((udpCksum.toInt() ushr 8) and 0xFF).toByte()
            resp[u + 7] = (udpCksum.toInt() and 0xFF).toByte()
        } else {
            resp[10] = 0
            resp[11] = 0
            val ipCksum = calculateIpv4Checksum(resp, 0, query.ipHeaderLength)
            resp[10] = ((ipCksum.toInt() ushr 8) and 0xFF).toByte()
            resp[11] = (ipCksum.toInt() and 0xFF).toByte()
        }

        return resp
    }

    /**
     * Calculates the 16-bit one's complement Internet Checksum for IPv4 headers (RFC 791).
     */
    fun calculateIpv4Checksum(data: ByteArray, offset: Int, length: Int): Short {
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
     * Verifies that the 16-bit one's complement checksum of an IPv4 header equals 0xFFFF
     * (meaning the sum including the checksum field evaluates to 0).
     */
    fun verifyIpv4Checksum(data: ByteArray, offset: Int = 0, length: Int): Boolean {
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
     * Calculates the mandatory RFC 2460 UDP checksum for IPv6 packets across the
     * IPv6 pseudo-header, UDP header, and payload.
     */
    fun calculateIpv6UdpChecksum(data: ByteArray, udpOffset: Int, udpLength: Int): Short {
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
        sum += 17L

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

        var cksum = (sum.toInt().inv() and 0xFFFF)
        if (cksum == 0) cksum = 0xFFFF // RFC 2460: Zero checksum transmitted as 0xFFFF
        return cksum.toShort()
    }
}
