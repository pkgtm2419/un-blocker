package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a domain blocking rule, regardless of its origin.
 *
 * Rules are keyed by [domain] (unique constraint). If the ML worker discovers that
 * a previously ML-flagged domain is whitelisted by the user, the row is deleted rather
 * than updated, then the Bloom Filter is recompiled without it.
 *
 * [source] maps to [com.unblocker.common.model.RuleSource] by name string:
 *   "STATIC" — shipped with the app bundle (compiled ad-list assets).
 *   "ML"     — inferred by the 4 AM TFLite batch worker.
 *   "USER"   — manually created/removed by the user via the whitelist screen.
 *
 * Table: `block_rules`
 */
@Entity(
    tableName = "block_rules",
    indices = [Index(value = ["domain"], unique = true)]
)
data class BlockRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,

    /** ML confidence score 0.0–1.0. Static/user rules default to 1.0. */
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float = 1.0f,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** One of "STATIC", "ML", "USER" — matches [RuleSource] enum names. */
    @ColumnInfo(name = "rule_source")
    val source: String
)
