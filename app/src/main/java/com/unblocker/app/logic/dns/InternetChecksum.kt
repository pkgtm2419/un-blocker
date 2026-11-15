package com.unblocker.app.logic.dns

/** One's-complement checksums used by IP and UDP packet builders. */
object InternetChecksum {
    /**
     * Computes the Internet checksum across a selected IPv4 header range.
     *
     * @param packet Bytes containing the header; zero the checksum field when generating a checksum.
     * @param offset Zero-based header byte offset.
     * @param length Header range length in bytes.
     * @return Checksum wire bits as a Short, without normalizing a zero result.
     * @throws IllegalArgumentException If the selected range is empty or outside packet.
     */
    fun ipv4Header(
        packet: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        require(offset >= 0 && length > 0 && offset <= packet.size - length)
        return finish(sumBytes(packet, offset, length), normalizeZero = false)
    }

    /**
     * Computes the IPv6 UDP pseudo-header and segment checksum.
     *
     * @param source Sixteen-byte source address.
     * @param destination Sixteen-byte destination address.
     * @param udpSegment Bytes containing the UDP header and payload.
     * @param offset Zero-based segment byte offset.
     * @param length Segment length in bytes, from 8 through 65535.
     * @return Checksum wire bits, mapping a zero result to 0xffff.
     * @throws IllegalArgumentException If addresses or segment bounds are invalid.
     */
    fun udpIpv6(
        source: ByteArray,
        destination: ByteArray,
        udpSegment: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        require(source.size == 16 && destination.size == 16)
        require(length in 8..0xffff && offset >= 0 && offset <= udpSegment.size - length)

        var sum = 0L
        sum = addBytes(sum, source, 0, source.size)
        sum = addBytes(sum, destination, 0, destination.size)
        sum = addWord(sum, (length ushr 16) and 0xffff)
        sum = addWord(sum, length and 0xffff)
        sum = addWord(sum, 0)
        sum = addWord(sum, 17)
        sum = addBytes(sum, udpSegment, offset, length)
        return finish(sum, normalizeZero = true)
    }

    /**
     * Computes the IPv4 TCP pseudo-header and segment checksum.
     *
     * @param source Four-byte source address.
     * @param destination Four-byte destination address.
     * @param tcpSegment Bytes containing the TCP header and payload.
     * @param offset Zero-based segment byte offset.
     * @param length Segment length in bytes, from 20 through 65535.
     * @return Checksum wire bits, mapping a zero result to 0xffff.
     * @throws IllegalArgumentException If addresses or segment bounds are invalid.
     */
    fun tcpIpv4(
        source: ByteArray,
        destination: ByteArray,
        tcpSegment: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        require(source.size == 4 && destination.size == 4)
        require(length in 20..0xffff && offset >= 0 && offset <= tcpSegment.size - length)

        var sum = 0L
        sum = addBytes(sum, source, 0, source.size)
        sum = addBytes(sum, destination, 0, destination.size)
        sum = addWord(sum, 6) // Protocol 6 = TCP
        sum = addWord(sum, length and 0xffff)
        sum = addBytes(sum, tcpSegment, offset, length)
        return finish(sum, normalizeZero = true)
    }

    /**
     * Computes the IPv6 TCP pseudo-header and segment checksum.
     *
     * @param source Sixteen-byte source address.
     * @param destination Sixteen-byte destination address.
     * @param tcpSegment Bytes containing the TCP header and payload.
     * @param offset Zero-based segment byte offset.
     * @param length Segment length in bytes, from 20 through 65535.
     * @return Checksum wire bits, mapping a zero result to 0xffff.
     * @throws IllegalArgumentException If addresses or segment bounds are invalid.
     */
    fun tcpIpv6(
        source: ByteArray,
        destination: ByteArray,
        tcpSegment: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        require(source.size == 16 && destination.size == 16)
        require(length in 20..0xffff && offset >= 0 && offset <= tcpSegment.size - length)

        var sum = 0L
        sum = addBytes(sum, source, 0, source.size)
        sum = addBytes(sum, destination, 0, destination.size)
        sum = addWord(sum, (length ushr 16) and 0xffff)
        sum = addWord(sum, length and 0xffff)
        sum = addWord(sum, 0)
        sum = addWord(sum, 6) // Next Header 6 = TCP
        sum = addBytes(sum, tcpSegment, offset, length)
        return finish(sum, normalizeZero = true)
    }

    private fun sumBytes(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Long = addBytes(0L, bytes, offset, length)

    private fun addBytes(
        initial: Long,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Long {
        var sum = initial
        var position = offset
        val end = offset + length
        while (position + 1 < end) {
            val word =
                ((bytes[position].toInt() and 0xff) shl 8) or
                    (bytes[position + 1].toInt() and 0xff)
            sum = addWord(sum, word)
            position += 2
        }
        if (position < end) sum = addWord(sum, (bytes[position].toInt() and 0xff) shl 8)
        return sum
    }

    private fun addWord(
        initial: Long,
        word: Int,
    ): Long {
        var sum = initial + (word and 0xffff)
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        return sum
    }

    private fun finish(
        initial: Long,
        normalizeZero: Boolean,
    ): Short {
        var sum = initial
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        val checksum = sum.toInt().inv() and 0xffff
        return (if (normalizeZero && checksum == 0) 0xffff else checksum).toShort()
    }
}
