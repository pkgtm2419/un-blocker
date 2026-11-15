package com.unblocker.common.model

/**
 * Parsed DNS query metadata extracted from an IP/UDP packet.
 *
 * Address arrays are retained directly; do not mutate them while using equality or hashing.
 *
 * @property domain Queried domain name as supplied by the parser.
 * @property isIpv6 Whether the enclosing packet uses IPv6.
 * @property srcIp Source address bytes: four for IPv4 or sixteen for IPv6.
 * @property dstIp Destination address bytes in the same address family.
 * @property srcPort Original UDP source port as an unsigned 16-bit value in an Int.
 * @property txId DNS transaction ID bits represented as a signed Short.
 */
data class DnsPacket(
    val domain: String,
    val isIpv6: Boolean = false,
    val srcIp: ByteArray = ByteArray(4),
    val dstIp: ByteArray = ByteArray(4),
    val srcPort: Int = 0,
    val txId: Short = 0,
) {
    /**
     * Compares metadata using address array contents.
     *
     * @param other Value to compare.
     * @return Whether all metadata and address bytes match.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DnsPacket) return false
        return domain == other.domain &&
            isIpv6 == other.isIpv6 &&
            srcIp.contentEquals(other.srcIp) &&
            dstIp.contentEquals(other.dstIp) &&
            srcPort == other.srcPort &&
            txId == other.txId
    }

    /**
     * Hashes metadata using address array contents.
     *
     * @return Hash consistent with [equals] while address arrays remain unchanged.
     */
    override fun hashCode(): Int {
        var result = domain.hashCode()
        result = 31 * result + isIpv6.hashCode()
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + srcPort
        result = 31 * result + txId
        return result
    }
}
