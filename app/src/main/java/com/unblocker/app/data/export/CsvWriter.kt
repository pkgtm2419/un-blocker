package com.unblocker.app.data.export

import java.io.Writer

/**
 * Escapes CSV fields and writes CRLF-terminated rows to caller-owned writers.
 */
object CsvWriter {
    /**
     * Escapes one field and prefixes formula-leading characters with an apostrophe.
     *
     * The leading characters checked are equals, plus, minus, at sign, tab, and carriage return.
     * Commas, quotes, and line breaks trigger RFC 4180 quoting with doubled embedded quotes.
     *
     * @param raw Unescaped field text.
     * @return Field text suitable for inclusion in a CSV row.
     */
    fun escapeCell(raw: String): String {
        var value = raw
        // Defend against CSV Formula Injection (DDE attack vectors)
        if (value.isNotEmpty()) {
            val firstChar = value[0]
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' ||
                firstChar == '\r'
            ) {
                value = "'$value"
            }
        }

        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Writes one escaped CSV row followed by CRLF without flushing or closing the writer.
     *
     * @param writer Caller-owned character output.
     * @param fields Unescaped field values in column order.
     * @throws java.io.IOException If the writer cannot accept the row.
     */
    fun writeRow(
        writer: Writer,
        fields: List<String>,
    ) {
        val row = fields.joinToString(separator = ",", postfix = "\r\n") { escapeCell(it) }
        writer.write(row)
    }
}
