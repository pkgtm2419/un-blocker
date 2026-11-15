package com.unblocker.app.logic.analysis

import java.io.Reader

/** Reads at most limit + 1 characters so corrupt lines cannot exhaust memory or CPU. */
internal object BoundedEvidenceInput {
    /**
     * Reads one bounded line without retaining more than the character limit.
     *
     * @param reader Input reader, whose ownership remains with the caller.
     * @param limit Maximum retained characters per line, including any carriage return.
     * @return Line without trailing carriage returns, or null at EOF before any characters.
     * @throws IllegalArgumentException If limit is nonpositive or a line exceeds it.
     * @throws java.io.IOException If reading fails.
     */
    fun line(
        reader: Reader,
        limit: Int,
    ): String? {
        require(limit > 0)
        val result = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (result.isEmpty()) null else result.toString().trimEnd('\r')
            if (c == 10) return result.toString().trimEnd('\r')
            if (result.length >= limit) throw IllegalArgumentException("Oversized evidence line")
            result.append(c.toChar())
        }
    }
}
