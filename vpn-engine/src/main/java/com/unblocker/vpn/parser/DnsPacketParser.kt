package com.unblocker.vpn.parser

import com.unblocker.common.model.DnsPacket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses raw IP/UDP packet bytes into [DnsPacket] domain objects and builds DNS wire-format
 * block responses (NXDOMAIN / REFUSED).
 *
 * All methods are stateless and thread-safe; they may be called concurrently from the VPN
 * packet-processing loop without synchronization.
 *
 * Pipeline:
 *  raw bytes → [extractDnsQuery] → [DnsPacket] → [buildBlockedDnsResponse]
 */
object DnsPacketParser {

    private const val DNS_MIN_LENGTH = 12  // Minimum valid DNS header length
    private const val MAX_DOMAIN_LENGTH = 253

    /**
     * Attempts to parse a DNS query from the given raw IP packet [buffer].
     *
     * The parser handles:
     *  - IPv4 and IPv6 outer packets
     *  - IPv6 extension headers (hop-by-hop, routing, fragmentation, destination)
     *  - UDP port 53 destination filter
     *  - DNS wire-format label decoding
     *
     * @param buffer     Raw packet bytes.
     * @param length     Number of valid bytes in [buffer].
     * @return A [DnsPacket] if the packet is a valid DNS query, null otherwise.
     */
    fun extractDnsQuery(buffer: ByteArray, length: Int): DnsPacket? {
        val ip = IpPacket.parse(buffer, length) ?: return null
        if (!ip.isUdp) return null

        val udp = UdpPacket.parse(buffer, ip.ipHeaderLength, length) ?: return null
        if (!udp.isDns) return null

        val dnsStart = udp.payloadOffset
        val dnsLength = udp.payloadLength
        if (dnsLength < DNS_MIN_LENGTH) return null

        // Verify it is a DNS query (QR bit = 0)
        val flags = ((buffer[dnsStart + 2].toInt() and 0xFF) shl 8) or (buffer[dnsStart + 3].toInt() and 0xFF)
        if ((flags and 0x8000) != 0) return null  // Response packet — skip

        val qdCount = ((buffer[dnsStart + 4].toInt() and 0xFF) shl 8) or (buffer[dnsStart + 5].toInt() and 0xFF)
        if (qdCount <= 0) return null

        val domain = decodeDnsName(buffer, dnsStart + 12, dnsStart + dnsLength) ?: return null
        val txId = ((buffer[dnsStart].toInt() and 0xFF) shl 8 or (buffer[dnsStart + 1].toInt() and 0xFF)).toShort()

        return DnsPacket(
            domain = domain,
            isIpv6 = ip.isIpv6,
            srcIp = ip.srcIp,
            dstIp = ip.dstIp,
            srcPort = udp.srcPort,
            txId = txId
        )
    }

    /**
     * Builds a synthetic NXDOMAIN DNS response packet (IP+UDP+DNS) that the VPN
     * writes back to the local tun interface to immediately block the queried domain.
     *
     * The response mirrors the original query's IP header direction:
     *  src/dst IPs and ports are swapped so the device receives a valid local reply.
     *
     * @param query     The parsed DNS query to block.
     * @param rcode     DNS RCODE: 3 = NXDOMAIN (default), 5 = REFUSED.
     * @return Raw IP+UDP+DNS response bytes ready to write to the tun fd.
     */
    fun buildBlockedDnsResponse(query: DnsPacket, rcode: Int = 3): ByteArray {
        // Build minimal DNS response: header only (12 bytes), no answer records
        val dnsResponse = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
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
     * Wraps a real upstream DNS response payload [dnsBytes] in IP+UDP headers
     * to deliver it back to the app through the tun interface.
     *
     * @param query    The original DNS query metadata (src/dst IPs and ports).
     * @param dnsBytes Raw DNS response bytes from the upstream resolver.
     * @return         Full IP+UDP+DNS packet ready to write to the tun fd.
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
        val sb = StringBuilder(64)
        var pos = offset
        var jumped = false
        var jumpCount = 0

        while (pos < end) {
            val len = buffer[pos].toInt() and 0xFF

            // Compression pointer: top two bits are 11
            if (len and 0xC0 == 0xC0) {
                if (pos + 1 >= end) return null
                if (jumpCount++ > 10) return null  // Guard against infinite loops
                val ptrOffset = ((len and 0x3F) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
                if (!jumped) pos += 2
                pos = ptrOffset
                jumped = true
                continue
            }

            if (len == 0) break  // End of name

            pos++
            if (pos + len > end) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append(buffer[pos + i].toInt().toChar())
            }
            pos += len

            if (sb.length > MAX_DOMAIN_LENGTH) return null
        }

        val domain = sb.toString().lowercase().trimEnd('.')
        return if (domain.isNotEmpty()) domain else null
    }

    /**
     * Wraps [dnsBytes] in a UDP + IP header for the given [query], swapping src/dst
     * so the packet flows back from the VPN to the requesting application.
     */
    private fun wrapInIpUdp(query: DnsPacket, dnsBytes: ByteArray): ByteArray {
        val udpLength = 8 + dnsBytes.size
        val ipHeaderLength = if (query.isIpv6) 40 else 20
        val buf = ByteBuffer.allocate(ipHeaderLength + udpLength).order(ByteOrder.BIG_ENDIAN)

        if (query.isIpv6) {
            buf.putInt(0x60000000)           // Version + Traffic Class + Flow Label
            buf.putShort(udpLength.toShort()) // Payload Length
            buf.put(17.toByte())              // Next Header = UDP
            buf.put(64.toByte())              // Hop Limit
            buf.put(query.dstIp)              // Swap: original dst → new src
            buf.put(query.srcIp)              // Swap: original src → new dst
        } else {
            buf.put(0x45.toByte())            // IPv4, IHL=5
            buf.put(0)
            buf.putShort((20 + udpLength).toShort())
            buf.putShort(0)
            buf.putShort(0x4000.toShort())    // DF flag
            buf.put(64)                        // TTL
            buf.put(17)                        // Protocol = UDP
            buf.putShort(0)                    // Checksum (filled below)
            buf.put(query.dstIp)              // Swap
            buf.put(query.srcIp)
            // Compute IPv4 header checksum
            val cksum = ipv4Checksum(buf.array(), 0, 20)
            buf.putShort(10, cksum)
        }

        // UDP header (swapped ports: response comes from port 53 back to query's srcPort)
        buf.putShort(53.toShort())                 // UDP src = DNS port
        buf.putShort(query.srcPort.toShort())      // UDP dst = original src
        buf.putShort(udpLength.toShort())
        buf.putShort(0)                            // Checksum (optional for IPv4)
        buf.put(dnsBytes)

        return buf.array()
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
