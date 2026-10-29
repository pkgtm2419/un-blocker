package com.unblocker.app.logic

import java.net.IDN
import java.util.Locale

/** Canonical validation boundary for DNS names accepted by the filtering engine. */
object DomainName {
    fun normalize(raw: String): String? {
        val domain = raw.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (domain.isEmpty() || domain.length > 253) return null

        val labels = domain.split('.')
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 || label.first() == '-' || label.last() == '-' ||
                    label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }
            }) return null

        return domain
    }

    /**
     * Canonicalizes user-entered or imported domain names into valid DNS wire ASCII.
     * Converts internationalized domain names (IDNs) via Punycode (IDN.toASCII with STD3 rules).
     */
    fun normalizeUserEntry(raw: String): String? {
        val trimmed = raw.trim().trimEnd('.')
        if (trimmed.isEmpty() || trimmed.length > 253) return null
        val ascii = try {
            IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES)
        } catch (_: Exception) {
            return null
        }
        return normalize(ascii)
    }

    /** Canonicalizes one DNS wire label without trimming or changing label boundaries. */
    fun canonicalWireLabel(packet: ByteArray, offset: Int, length: Int): String? {
        if (length !in 1..63 || offset < 0 || offset > packet.size - length) return null
        val chars = CharArray(length)
        for (index in 0 until length) {
            val value = packet[offset + index].toInt() and 0xff
            chars[index] = when (value) {
                in 'A'.code..'Z'.code -> (value + ('a'.code - 'A'.code)).toChar()
                in 'a'.code..'z'.code, in '0'.code..'9'.code, '-'.code, '_'.code -> value.toChar()
                else -> return null
            }
        }
        if (chars.first() == '-' || chars.last() == '-') return null
        return String(chars)
    }
}
