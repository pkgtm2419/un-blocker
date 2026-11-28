package com.unblocker.app.data.export

import com.unblocker.data.db.entity.DomainStatEntity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports readable local domain aggregates as UTF-8 CSV (v2 columns) or schema-versioned JSON (WP-22 / F31).
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
            "LastOutcome",
            "FailCount",
            "LastRule",
            "LastSource",
            "LastNetworkType",
        )

    /**
     * Writes aggregate rows with CSV v2 headers, then flushes and closes [outputStream].
     *
     * Preserves the legacy 7-column prefix while appending `LastOutcome`, `FailCount`, `LastRule`,
     * `LastSource`, and `LastNetworkType`. All cells are passed through [CsvWriter] formula escaping.
     */
    fun exportToCsv(
        stats: List<DomainStatEntity>,
        outputStream: OutputStream,
    ): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            CsvWriter.writeRow(writer, HEADERS)
            for (stat in stats) {
                val decisionLabel =
                    when {
                        stat.lastOutcome == DomainStatEntity.OUTCOME_UPSTREAM_FAIL -> "FAILED"
                        stat.lastDecision == 1 -> "BLOCKED"
                        else -> "ALLOWED"
                    }
                val outcomeLabel = outcomeLabelFor(stat)
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
                        outcomeLabel,
                        stat.failCount.toString(),
                        stat.lastRule,
                        stat.lastSource,
                        stat.lastNetworkType,
                    )
                CsvWriter.writeRow(writer, row)
            }
            writer.flush()
        }
        return stats.size
    }

    /**
     * Writes aggregate rows in schema-versioned UTF-8 JSON (`version = 2`) and closes [outputStream].
     */
    fun exportToJson(
        stats: List<DomainStatEntity>,
        outputStream: OutputStream,
    ): Int {
        val isoFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val records =
            stats.map { stat ->
                linkedMapOf<String, Any?>(
                    "domain" to stat.domain,
                    "lastOutcome" to outcomeLabelFor(stat),
                    "totalQueries" to stat.callCount,
                    "blockedQueries" to stat.blockedCount,
                    "allowedQueries" to stat.allowedCount,
                    "failCount" to stat.failCount,
                    "firstSeenUtc" to isoFormat.format(Date(stat.firstSeen)),
                    "lastSeenUtc" to isoFormat.format(Date(stat.lastSeen)),
                    "lastReasonCode" to stat.lastReason,
                    "lastRule" to stat.lastRule,
                    "lastSource" to stat.lastSource,
                    "lastNetworkType" to stat.lastNetworkType,
                )
            }
        val root =
            linkedMapOf<String, Any?>(
                "version" to 2,
                "exportedAtUtc" to isoFormat.format(Date()),
                "recordCount" to stats.size,
                "records" to records,
            )
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.write(PureJsonCodec.stringifyObject(root))
            writer.flush()
        }
        return stats.size
    }

    private fun outcomeLabelFor(stat: DomainStatEntity): String =
        when {
            stat.lastOutcome == DomainStatEntity.OUTCOME_UPSTREAM_FAIL -> "UPSTREAM_FAIL"
            stat.lastOutcome == DomainStatEntity.OUTCOME_BLOCKED || stat.lastDecision == 1 -> "BLOCKED"
            else -> "ALLOWED"
        }
}
