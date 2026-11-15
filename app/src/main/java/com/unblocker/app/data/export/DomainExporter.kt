package com.unblocker.app.data.export

import com.unblocker.data.db.entity.DomainStatEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports readable local domain aggregates as UTF-8 CSV with escaped cells.
 */
object DomainExporter {
    private val HEADERS =
        listOf(
            "Domain",
            "LastDecision",
            "TotalQueries",
            "BlockedQueries",
            "AllowedQueries",
            "FirstSeen",
            "LastSeen",
        )

    /**
     * Writes aggregate rows with a header, then flushes and closes the supplied stream.
     *
     * Timestamps use the device default time zone and a fixed US-locale display format.
     *
     * @param stats Aggregate snapshots to export in their supplied order.
     * @param outputStream Destination whose ownership is transferred to this call.
     * @return Number of data rows written, excluding the header.
     * @throws java.io.IOException If writing, flushing, or closing the stream fails.
     */
    fun exportToCsv(
        stats: List<DomainStatEntity>,
        outputStream: OutputStream,
    ): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            CsvWriter.writeRow(writer, HEADERS)
            for (stat in stats) {
                val decisionLabel = if (stat.lastDecision == 1) "BLOCKED" else "ALLOWED"
                val firstSeenText = dateFormat.format(Date(stat.firstSeen))
                val lastSeenText = dateFormat.format(Date(stat.lastSeen))
                val row =
                    listOf(
                        stat.domain,
                        decisionLabel,
                        stat.callCount.toString(),
                        stat.blockedCount.toString(),
                        stat.allowedCount.toString(),
                        firstSeenText,
                        lastSeenText,
                    )
                CsvWriter.writeRow(writer, row)
            }
            writer.flush()
        }
        return stats.size
    }
}
