package com.unblocker.app.data.export

import com.unblocker.data.db.entity.DomainStatEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DomainExporter {

    private val HEADERS = listOf(
        "Domain",
        "LastDecision",
        "TotalQueries",
        "BlockedQueries",
        "AllowedQueries",
        "FirstSeen",
        "LastSeen"
    )

    fun exportToCsv(stats: List<DomainStatEntity>, outputStream: OutputStream): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            CsvWriter.writeRow(writer, HEADERS)
            for (stat in stats) {
                val decisionStr = if (stat.lastDecision == 1) "BLOCKED" else "ALLOWED"
                val firstSeenStr = dateFormat.format(Date(stat.firstSeen))
                val lastSeenStr = dateFormat.format(Date(stat.lastSeen))
                val row = listOf(
                    stat.domain,
                    decisionStr,
                    stat.callCount.toString(),
                    stat.blockedCount.toString(),
                    stat.allowedCount.toString(),
                    firstSeenStr,
                    lastSeenStr
                )
                CsvWriter.writeRow(writer, row)
            }
            writer.flush()
        }
        return stats.size
    }
}
