package com.unblocker.vpn.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a parsed IPv4 or IPv6 packet header.
 *
 * @param isIpv6         True for IPv6, false for IPv4.
 * @param protocol       IP protocol number (17 = UDP, 6 = TCP).
 * @param srcIp          Source IP address bytes.
 * @param dstIp          Destination IP address bytes.
 * @param ipHeaderLength Length of the IP header in bytes.
 */
data class IpPacket(
    val isIpv6: Boolean,
    val protocol: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val ipHeaderLength: Int
) {
    companion object {
        private const val PROTOCOL_UDP = 17

        /**
         * Parses the IP header from [buffer].
         * Returns null for unsupported or malformed packets.
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
            val ihl = (buffer[0].toInt() and 0x0F) * 4
            if (ihl < 20 || length < ihl + 8) return null
            val protocol = buffer[9].toInt() and 0xFF
            val srcIp = buffer.copyOfRange(12, 16)
            val dstIp = buffer.copyOfRange(16, 20)
            return IpPacket(
                isIpv6 = false,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp,
                ipHeaderLength = ihl
            )
        }

        private fun parseIpv6(buffer: ByteArray, length: Int): IpPacket? {
            if (length < 40 + 8) return null
            // Walk extension headers to find the final protocol
            var nextHeader = buffer[6].toInt() and 0xFF
            var offset = 40
            while (nextHeader in setOf(0, 43, 60, 44)) {
                if (offset + 2 > length) return null
                val extLen = if (nextHeader == 44) 8 else ((buffer[offset + 1].toInt() and 0xFF) + 1) * 8
                if (offset + extLen + 8 > length) return null
                nextHeader = buffer[offset].toInt() and 0xFF
                offset += extLen
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

    val isUdp: Boolean get() = protocol == PROTOCOL_UDP

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpPacket) return false
        return isIpv6 == other.isIpv6 && protocol == other.protocol &&
            srcIp.contentEquals(other.srcIp) && dstIp.contentEquals(other.dstIp) &&
            ipHeaderLength == other.ipHeaderLength
    }

    override fun hashCode(): Int {
        var result = isIpv6.hashCode()
        result = 31 * result + protocol
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + ipHeaderLength
        return result
    }
}
