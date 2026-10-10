package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists timestamped history for domains.
 * Bounded to at most 50 events per domain and a 7-day retention period.
 *
 * Table: `domain_events`
 */
@Entity(
    tableName = "domain_events",
    indices = [
        Index(value = ["domain", "ts"]),
        Index(value = ["ts"])
    ]
)
data class DomainEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "ts")
    val ts: Long,

    /** 0 for ALLOWED, 1 for BLOCKED */
    @ColumnInfo(name = "decision")
    val decision: Int
)
