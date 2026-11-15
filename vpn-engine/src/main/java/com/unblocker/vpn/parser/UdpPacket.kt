package com.unblocker.vpn.parser

/**
 * Parsed UDP ports and payload range within an enclosing packet.
 *
 * @property srcPort Unsigned 16-bit source port represented as an Int.
 * @property dstPort Unsigned 16-bit destination port represented as an Int.
 * @property payloadOffset First payload byte index in the enclosing packet storage.
 * @property payloadLength Payload length in bytes, excluding the eight-byte UDP header.
 */
data class UdpPacket(
    val srcPort: Int,
    val dstPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
) {
    /**
     * Whether the destination port is 53; this alone does not validate the DNS payload.
     */
    val isDns: Boolean get() = dstPort == DNS_PORT

    /**
     * UDP header parsing entry point and DNS port identifier.
     */
    companion object {
        /**
         * Standard DNS destination port used by [isDns].
         */
        const val DNS_PORT = 53
        private const val UDP_HEADER_SIZE = 8

        /**
         * Reads UDP ports and validates its payload against the declared enclosing packet length.
         *
         * @param buffer Raw packet storage.
         * @param offset Nonnegative byte index of the UDP header.
         * @param totalLength Declared enclosing packet length in bytes, at most [buffer] size.
         * @return Header metadata, or null when declared lengths cannot contain the datagram.
         * @throws IndexOutOfBoundsException If caller bounds permit access outside the supplied array.
         */
        fun parse(
            buffer: ByteArray,
            offset: Int,
            totalLength: Int,
        ): UdpPacket? {
            if (offset + UDP_HEADER_SIZE > totalLength) return null
            val srcPort = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            val dstPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
            val udpLength = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
            val payloadLength = udpLength - UDP_HEADER_SIZE
            if (payloadLength < 0) return null
            val payloadOffset = offset + UDP_HEADER_SIZE
            if (payloadOffset + payloadLength > totalLength) return null
            return UdpPacket(srcPort, dstPort, payloadOffset, payloadLength)
        }
    }
}
