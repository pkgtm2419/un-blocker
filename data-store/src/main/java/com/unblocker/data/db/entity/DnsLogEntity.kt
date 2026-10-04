package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists every DNS request intercepted by the VPN tunnel.
 *
 * Indexed on both [timestamp] (for time-range pruning) and [domain] (for deduplication
 * during ML batch analysis). The [isAnalyzed] flag is the ML worker's cursor — it prevents
 * the same domain from being re-scored across consecutive nightly runs.
 *
 * Table: `dns_logs`
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

    /** True when the Bloom Filter matched and a NXDOMAIN response was returned. */
    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean,

    /** True once the nightly ML worker has processed this entry. */
    @ColumnInfo(name = "is_analyzed", defaultValue = "0")
    val isAnalyzed: Boolean = false
)
