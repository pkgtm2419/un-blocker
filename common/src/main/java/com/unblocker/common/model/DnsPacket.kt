package com.unblocker.common.model

/**
 * Represents a parsed DNS query extracted from a raw IP/UDP packet.
 *
 * @param domain      The fully-qualified domain name being queried (e.g. "ads.example.com").
 * @param isIpv6      True when the enclosing IP packet is IPv6; false for IPv4.
 * @param srcIp       Source IP bytes (4 bytes for IPv4, 16 bytes for IPv6).
 * @param dstIp       Destination IP bytes.
 * @param srcPort     UDP source port of the original DNS query.
 * @param txId        DNS transaction ID, used to match responses.
 */
data class DnsPacket(
    val domain: String,
    val isIpv6: Boolean = false,
    val srcIp: ByteArray = ByteArray(4),
    val dstIp: ByteArray = ByteArray(4),
    val srcPort: Int = 0,
    val txId: Short = 0
) {
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
