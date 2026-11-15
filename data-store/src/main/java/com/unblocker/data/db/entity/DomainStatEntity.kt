package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local query aggregates keyed by the readable domain.
 *
 * App QueryLogSink periodically prunes aggregates older than 30 days and beyond 10,000 domains.
 * These maintenance targets can be exceeded between runs.
 *
 * @property domain Readable domain primary key.
 * @property callCount Total recorded queries.
 * @property blockedCount Recorded blocked queries.
 * @property allowedCount Recorded allowed queries.
 * @property firstSeen First observation time in milliseconds since the Unix epoch.
 * @property lastSeen Latest supplied observation time in milliseconds since the Unix epoch.
 * @property lastDecision Latest decision code: 0 for allowed or 1 for blocked.
 * @property lastReason Latest app-defined reason code.
 */
@Entity(
    tableName = "domain_stats",
    indices = [
        Index(value = ["last_seen"]),
        Index(value = ["call_count"])
    ]
)
data class DomainStatEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "call_count")
    val callCount: Long,

    @ColumnInfo(name = "blocked_count")
    val blockedCount: Long,

    @ColumnInfo(name = "allowed_count")
    val allowedCount: Long,

    @ColumnInfo(name = "first_seen")
    val firstSeen: Long,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long,

    @ColumnInfo(name = "last_decision")
    val lastDecision: Int,

    @ColumnInfo(name = "last_reason")
    val lastReason: Int
)
