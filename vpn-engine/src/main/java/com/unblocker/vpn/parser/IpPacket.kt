package com.unblocker.vpn.parser


/**
 * Parsed IP metadata with directly retained address arrays.
 *
 * Do not mutate address arrays while relying on equality or hashing.
 *
 * @property isIpv6 Whether the packet uses IPv6.
 * @property protocol Final IP protocol number, such as 17 for UDP or 6 for TCP.
 * @property srcIp Source bytes: four for IPv4 or sixteen for IPv6.
 * @property dstIp Destination address bytes in the same address family.
 * @property ipHeaderLength Payload offset in bytes, including supported IPv6 extension headers.
 */
data class IpPacket(
    val isIpv6: Boolean,
    val protocol: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val ipHeaderLength: Int
) {
    /**
     * IP header parsing entry point and protocol constants.
     */
    companion object {
        private const val PROTOCOL_UDP = 17

        /**
         * Reads IP addresses and the payload offset without validating all IP invariants.
         *
         * IPv6 walks supported hop-by-hop, routing, destination, and fragment headers. Fragment fields
         * and IP checksums are not validated here.
         *
         * @param buffer Raw packet storage, stable during parsing.
         * @param length Declared valid byte count; must not exceed [buffer] size.
         * @return Header metadata, or null for unsupported versions or rejected lengths.
         * @throws IndexOutOfBoundsException If declared bounds permit access outside the array.
         */
        fun parse(buffer: ByteArray, length: Int): IpPacket? {
            if (length < 20) return null
            val version = (buffer[0].toInt() shr 4) and 0x0F

            return when (version) {
                4 -> parseIpv4(buffer, length)
                6 -> parseIpv6(buffer, length)
                else -> null
            }
        }

        private fun parseIpv4(buffer: ByteArray, length: Int): IpPacket? {
            val headerLength = (buffer[0].toInt() and 0x0F) * 4
            if (headerLength < 20 || length < headerLength + 8) return null
            val protocol = buffer[9].toInt() and 0xFF
            val srcIp = buffer.copyOfRange(12, 16)
            val dstIp = buffer.copyOfRange(16, 20)
            return IpPacket(
                isIpv6 = false,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp,
                ipHeaderLength = headerLength
            )
        }

        private fun parseIpv6(buffer: ByteArray, length: Int): IpPacket? {
            if (length < 40 + 8) return null
            // Walk extension headers to find the final protocol
            var nextHeader = buffer[6].toInt() and 0xFF
            var offset = 40
            while (nextHeader in setOf(0, 43, 60, 44)) {
                if (offset + 2 > length) return null
                val extensionHeaderLength = if (nextHeader == 44) 8 else ((buffer[offset + 1].toInt() and 0xFF) + 1) * 8
                if (offset + extensionHeaderLength + 8 > length) return null
                nextHeader = buffer[offset].toInt() and 0xFF
                offset += extensionHeaderLength
            }
            val srcIp = buffer.copyOfRange(8, 24)
            val dstIp = buffer.copyOfRange(24, 40)
            return IpPacket(
                isIpv6 = true,
                protocol = nextHeader,
                srcIp = srcIp,
                dstIp = dstIp,
                ipHeaderLength = offset
            )
        }
    }

    /**
     * Whether the parsed final protocol number is UDP.
     */
    val isUdp: Boolean get() = protocol == PROTOCOL_UDP

    /**
     * Compares metadata using address array contents.
     *
     * @param other Value to compare.
     * @return Whether protocol, offsets, address family, and address bytes match.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpPacket) return false
        return isIpv6 == other.isIpv6 && protocol == other.protocol &&
            srcIp.contentEquals(other.srcIp) && dstIp.contentEquals(other.dstIp) &&
            ipHeaderLength == other.ipHeaderLength
    }

    /**
     * Hashes metadata using address array contents.
     *
     * @return Hash consistent with [equals] while address arrays remain unchanged.
     */
    override fun hashCode(): Int {
        var result = isIpv6.hashCode()
        result = 31 * result + protocol
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + ipHeaderLength
        return result
    }
}
