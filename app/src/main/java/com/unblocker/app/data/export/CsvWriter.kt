package com.unblocker.app.data.export

import java.io.Writer

object CsvWriter {

    /**
     * Sanitizes a cell value for RFC 4180 CSV compliance and defends against CSV Formula Injection.
     * If a cell begins with '=', '+', '-', or '@', it is prepended with a single quote '\''.
     * If the cell contains commas, quotes, or newlines, it is enclosed in double quotes and inner quotes are doubled.
     */
    fun escapeCell(raw: String): String {
        var value = raw
        // Defend against CSV Formula Injection (DDE attack vectors)
        if (value.isNotEmpty()) {
            val firstChar = value[0]
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
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

    fun writeRow(writer: Writer, fields: List<String>) {
        val row = fields.joinToString(separator = ",", postfix = "\r\n") { escapeCell(it) }
        writer.write(row)
    }
}
