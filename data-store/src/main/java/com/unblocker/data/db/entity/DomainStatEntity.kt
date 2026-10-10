package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists aggregate statistics per unique domain.
 * Exactly one row per domain, meeting the WP-3 specification.
 *
 * Table: `domain_stats`
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

    /** 0 for ALLOWED, 1 for BLOCKED */
    @ColumnInfo(name = "last_decision")
    val lastDecision: Int,

    @ColumnInfo(name = "last_reason")
    val lastReason: Int
)
