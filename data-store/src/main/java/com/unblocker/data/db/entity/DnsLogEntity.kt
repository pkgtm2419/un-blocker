package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Legacy DNS request row used by the separate library analysis worker.
 *
 * Domains are plaintext. The analysis flag is per row, so another row with the same name
 * can still be analyzed later.
 *
 * @property id Generated row ID; zero requests automatic assignment on insert.
 * @property domain Readable queried domain.
 * @property timestamp Observation time in milliseconds since the Unix epoch.
 * @property isBlocked Blocking decision supplied by the caller.
 * @property isAnalyzed Whether this row has been marked as processed.
 */
@Entity(
    tableName = "dns_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["domain"]),
        Index(value = ["is_analyzed"])
    ]
)
data class DnsLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean,

    @ColumnInfo(name = "is_analyzed", defaultValue = "0")
    val isAnalyzed: Boolean = false
)
