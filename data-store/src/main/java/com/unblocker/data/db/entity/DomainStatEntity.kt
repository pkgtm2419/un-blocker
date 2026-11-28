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
 * @property lastReason Latest app-defined reason code (e.g., [DecisionReason.ordinal] or [DetectionMethod.ordinal]).
 * @property lastOutcome Latest execution outcome ([OUTCOME_ALLOWED], [OUTCOME_BLOCKED], [OUTCOME_UPSTREAM_FAIL]).
 * @property lastRule Matched rule pattern or host associated with the latest decision, when available.
 * @property lastSource Matched list/policy attribution label associated with the latest decision.
 * @property failCount Cumulative upstream resolution failures recorded for this domain.
 * @property lastNetworkType Transport classification (`wifi`, `cellular`, `other`) when observed.
 */
@Entity(
    tableName = "domain_stats",
    indices = [
        Index(value = ["last_seen"]),
        Index(value = ["call_count"]),
    ],
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
    val lastReason: Int,
    @ColumnInfo(name = "last_outcome", defaultValue = "0")
    val lastOutcome: Int = OUTCOME_ALLOWED,
    @ColumnInfo(name = "last_rule", defaultValue = "")
    val lastRule: String = "",
    @ColumnInfo(name = "last_source", defaultValue = "")
    val lastSource: String = "",
    @ColumnInfo(name = "fail_count", defaultValue = "0")
    val failCount: Long = 0L,
    @ColumnInfo(name = "last_network_type", defaultValue = "")
    val lastNetworkType: String = "",
) {
    /**
     * Execution outcome constants recorded in [lastOutcome].
     */
    companion object {
        const val OUTCOME_ALLOWED: Int = 0
        const val OUTCOME_BLOCKED: Int = 1
        const val OUTCOME_UPSTREAM_FAIL: Int = 2
    }
}
