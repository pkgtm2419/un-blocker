package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Timestamped local query event containing a readable domain.
 *
 * App QueryLogSink maintenance targets 24 hours of retention and 1,000 events globally.
 * Pruning is periodic, so these are not per-insert bounds. The DAO's 50-row read default
 * is a display limit, not a per-domain storage cap.
 *
 * @property id Generated row ID; zero requests automatic assignment on insert.
 * @property domain Readable queried domain.
 * @property ts Observation time in milliseconds since the Unix epoch.
 * @property decision Caller decision code: 0 for allowed or 1 for blocked.
 */
@Entity(
    tableName = "domain_events",
    indices = [
        Index(value = ["domain", "ts"]),
        Index(value = ["ts"]),
    ],
)
data class DomainEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "ts")
    val ts: Long,
    @ColumnInfo(name = "decision")
    val decision: Int,
)
