package com.unblocker.app.logic.dns

/** One's-complement checksums used by IP and UDP packet builders. */
object InternetChecksum {
    fun ipv4Header(packet: ByteArray, offset: Int, length: Int): Short {
        require(offset >= 0 && length > 0 && offset <= packet.size - length)
        return finish(sumBytes(packet, offset, length), normalizeZero = false)
    }

    fun udpIpv6(
        source: ByteArray,
        destination: ByteArray,
        udpSegment: ByteArray,
        offset: Int,
        length: Int
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

    fun tcpIpv4(
        source: ByteArray,
        destination: ByteArray,
        tcpSegment: ByteArray,
        offset: Int,
        length: Int
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

    fun tcpIpv6(
        source: ByteArray,
        destination: ByteArray,
        tcpSegment: ByteArray,
        offset: Int,
        length: Int
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

    private fun sumBytes(bytes: ByteArray, offset: Int, length: Int): Long =
        addBytes(0L, bytes, offset, length)

    private fun addBytes(initial: Long, bytes: ByteArray, offset: Int, length: Int): Long {
        var sum = initial
        var position = offset
        val end = offset + length
        while (position + 1 < end) {
            val word = ((bytes[position].toInt() and 0xff) shl 8) or
                (bytes[position + 1].toInt() and 0xff)
            sum = addWord(sum, word)
            position += 2
        }
        if (position < end) sum = addWord(sum, (bytes[position].toInt() and 0xff) shl 8)
        return sum
    }

    private fun addWord(initial: Long, word: Int): Long {
        var sum = initial + (word and 0xffff)
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        return sum
    }

    private fun finish(initial: Long, normalizeZero: Boolean): Short {
        var sum = initial
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        val checksum = sum.toInt().inv() and 0xffff
        return (if (normalizeZero && checksum == 0) 0xffff else checksum).toShort()
    }
}
