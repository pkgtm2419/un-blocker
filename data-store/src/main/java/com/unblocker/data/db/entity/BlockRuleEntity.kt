package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent block rule keyed by the readable domain string.
 *
 * The source stores a RuleSource enum name; this entity does not enforce filtering policy.
 *
 * @property domain Domain primary key.
 * @property confidenceScore Confidence conventionally from 0 to 1; not validated here.
 * @property createdAt Creation time in milliseconds since the Unix epoch.
 * @property source RuleSource name, conventionally STATIC, ML, or USER.
 */
@Entity(
    tableName = "block_rules",
    indices = [Index(value = ["domain"], unique = true)],
)
data class BlockRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float = 1.0f,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "rule_source")
    val source: String,
)
