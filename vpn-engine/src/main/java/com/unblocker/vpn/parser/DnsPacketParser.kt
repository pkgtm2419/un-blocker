package com.unblocker.vpn.parser

import com.unblocker.common.model.DnsPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stateless library DNS query extraction and reverse-direction IP/UDP packet construction.
 *
 * This implementation is separate from the active app packet utilities. Its response wrapper
 * sets the UDP checksum to zero for both address families, including IPv6; callers must not
 * assume it produces a complete IPv6 UDP checksum.
 */
object DnsPacketParser {

    private const val UDP_HEADER_LENGTH = 8
    private const val IPV4_HEADER_LENGTH = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val DNS_PORT = 53
    private const val DNS_MIN_LENGTH = 12  // Minimum valid DNS header length
    private const val MAX_DOMAIN_LENGTH = 253

    /**
     * Extracts a UDP port-53 query name and addressing metadata.
     *
     * Selected lengths, QR, and question count are checked. This is not full question, fragment,
     * checksum, or response validation; compression pointers follow this implementation's offsets.
     *
     * @param buffer Raw IP packet storage, stable during parsing.
     * @param length Declared valid prefix length in bytes, at most [buffer] size.
     * @return Query metadata, or null for rejected input.
     * @throws IndexOutOfBoundsException If declared bounds permit access beyond the supplied array.
     */
    fun extractDnsQuery(buffer: ByteArray, length: Int): DnsPacket? {
        val ipPacket = IpPacket.parse(buffer, length) ?: return null
        if (!ipPacket.isUdp) return null

        val udpPacket = UdpPacket.parse(buffer, ipPacket.ipHeaderLength, length) ?: return null
        if (!udpPacket.isDns) return null

        val dnsStart = udpPacket.payloadOffset
        val dnsLength = udpPacket.payloadLength
        if (dnsLength < DNS_MIN_LENGTH) return null

        // Verify it is a DNS query (QR bit = 0)
        val flags = ((buffer[dnsStart + 2].toInt() and 0xFF) shl 8) or (buffer[dnsStart + 3].toInt() and 0xFF)
        if ((flags and 0x8000) != 0) return null  // Response packet — skip

        val qdCount = ((buffer[dnsStart + 4].toInt() and 0xFF) shl 8) or (buffer[dnsStart + 5].toInt() and 0xFF)
        if (qdCount <= 0) return null

        val domain = decodeDnsName(buffer, dnsStart + DNS_MIN_LENGTH, dnsStart + dnsLength) ?: return null
        val txId = ((buffer[dnsStart].toInt() and 0xFF) shl 8 or (buffer[dnsStart + 1].toInt() and 0xFF)).toShort()

        return DnsPacket(
            domain = domain,
            isIpv6 = ipPacket.isIpv6,
            srcIp = ipPacket.srcIp,
            dstIp = ipPacket.dstIp,
            srcPort = udpPacket.srcPort,
            txId = txId
        )
    }

    /**
     * Builds a header-only DNS response with no question or answer records, wrapped in IP/UDP.
     *
     * Addresses and ports are reversed. The shared wrapper leaves the UDP checksum zero for both
     * address families, so it does not synthesize the mandatory IPv6 UDP checksum.
     *
     * @param query Query metadata with address byte arrays matching the address family.
     * @param rcode DNS result code; only its low four bits are encoded, conventionally 3 or 5.
     * @return New IP/UDP packet containing the twelve-byte DNS response header.
     * @throws java.nio.BufferOverflowException If address arrays exceed the allocated header space.
     */
    fun buildBlockedDnsResponse(query: DnsPacket, rcode: Int = 3): ByteArray {
        // Build minimal DNS response: header only (12 bytes), no answer records
        val dnsResponse = ByteBuffer.allocate(DNS_MIN_LENGTH).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(query.txId)
            // QR=1 (response), AA=1, RCODE=rcode
            putShort((0x8400 or (rcode and 0x0F)).toShort())
            putShort(0)  // QDCOUNT
            putShort(0)  // ANCOUNT
            putShort(0)  // NSCOUNT
            putShort(0)  // ARCOUNT
        }
        val dnsBytes = dnsResponse.array()
        return wrapInIpUdp(query, dnsBytes)
    }

    /**
     * Wraps caller-validated DNS response bytes in reverse-direction IP/UDP headers.
     *
     * The shared wrapper leaves the UDP checksum zero, including for IPv6.
     *
     * @param query Original query metadata with correctly sized source and destination address arrays.
     * @param dnsBytes DNS response payload; matching the query is the caller's responsibility.
     * @return New IP/UDP packet containing the supplied DNS payload.
     * @throws java.nio.BufferOverflowException If supplied address arrays exceed the allocated space.
     */
    fun buildForwardedResponse(query: DnsPacket, dnsBytes: ByteArray): ByteArray {
        return wrapInIpUdp(query, dnsBytes)
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Decodes a DNS wire-format name (labels) starting at [offset] within [buffer].
     * Handles compressed pointers (RFC 1035 §4.1.4) defensively.
     */
    private fun decodeDnsName(buffer: ByteArray, offset: Int, end: Int): String? {
        val domainBuilder = StringBuilder(64)
        var position = offset
        var jumped = false
        var jumpCount = 0

        while (position < end) {
            val labelLength = buffer[position].toInt() and 0xFF

            // Compression pointer: top two bits are 11
            if (labelLength and 0xC0 == 0xC0) {
                if (position + 1 >= end) return null
                if (jumpCount++ > 10) return null  // Guard against infinite loops
                val pointerOffset = ((labelLength and 0x3F) shl 8) or (buffer[position + 1].toInt() and 0xFF)
                if (!jumped) position += 2
                position = pointerOffset
                jumped = true
                continue
            }

            if (labelLength == 0) break  // End of name

            position++
            if (position + labelLength > end) return null
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            for (i in 0 until labelLength) {
                domainBuilder.append(buffer[position + i].toInt().toChar())
            }
            position += labelLength

            if (domainBuilder.length > MAX_DOMAIN_LENGTH) return null
        }

        val domain = domainBuilder.toString().lowercase().trimEnd('.')
        return if (domain.isNotEmpty()) domain else null
    }

    /**
     * Wraps [dnsBytes] in a UDP + IP header for the given [query], swapping src/dst
     * so the packet flows back from the VPN to the requesting application.
     */
    private fun wrapInIpUdp(query: DnsPacket, dnsBytes: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsBytes.size
        val ipHeaderLength = if (query.isIpv6) IPV6_HEADER_LENGTH else IPV4_HEADER_LENGTH
        val packetBuffer = ByteBuffer.allocate(ipHeaderLength + udpLength).order(ByteOrder.BIG_ENDIAN)

        if (query.isIpv6) {
            packetBuffer.putInt(0x60000000)           // Version + Traffic Class + Flow Label
            packetBuffer.putShort(udpLength.toShort()) // Payload Length
            packetBuffer.put(17.toByte())              // Next Header = UDP
            packetBuffer.put(64.toByte())              // Hop Limit
            packetBuffer.put(query.dstIp)              // Swap: original dst → new src
            packetBuffer.put(query.srcIp)              // Swap: original src → new dst
        } else {
            packetBuffer.put(0x45.toByte())            // IPv4, IHL=5
            packetBuffer.put(0)
            packetBuffer.putShort((IPV4_HEADER_LENGTH + udpLength).toShort())
            packetBuffer.putShort(0)
            packetBuffer.putShort(0x4000.toShort())    // DF flag
            packetBuffer.put(64)                        // TTL
            packetBuffer.put(17)                        // Protocol = UDP
            packetBuffer.putShort(0)                    // Checksum (filled below)
            packetBuffer.put(query.dstIp)              // Swap
            packetBuffer.put(query.srcIp)
            // Compute IPv4 header checksum
            val checksum = ipv4Checksum(packetBuffer.array(), 0, IPV4_HEADER_LENGTH)
            packetBuffer.putShort(10, checksum)
        }

        // UDP header (swapped ports: response comes from port 53 back to query's srcPort)
        packetBuffer.putShort(DNS_PORT.toShort())                 // UDP src = DNS port
        packetBuffer.putShort(query.srcPort.toShort())      // UDP dst = original src
        packetBuffer.putShort(udpLength.toShort())
        packetBuffer.putShort(0)                            // Checksum (optional for IPv4)
        packetBuffer.put(dnsBytes)

        return packetBuffer.array()
    }

    /** One's complement 16-bit checksum for IPv4 headers. */
    private fun ipv4Checksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((length and 1) != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toShort()
    }
}
