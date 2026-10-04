package com.unblocker.vpn.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a parsed UDP header.
 *
 * @param srcPort    Source UDP port.
 * @param dstPort    Destination UDP port (53 = DNS).
 * @param payloadOffset Byte offset of the UDP payload in the original packet buffer.
 * @param payloadLength Length in bytes of the UDP payload.
 */
data class UdpPacket(
    val srcPort: Int,
    val dstPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int
) {
    val isDns: Boolean get() = dstPort == DNS_PORT

    companion object {
        const val DNS_PORT = 53
        private const val UDP_HEADER_SIZE = 8

        /**
         * Parses a UDP header from [buffer] starting at [offset].
         * Returns null if the buffer is too short or the UDP length is invalid.
         */
        fun parse(buffer: ByteArray, offset: Int, totalLength: Int): UdpPacket? {
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
