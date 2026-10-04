package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-managed whitelist — domains here are always allowed regardless of any block rule.
 *
 * When a user whitelists a domain from the false-positive dashboard:
 *  1. The matching [BlockRuleEntity] row is deleted.
 *  2. A [WhitelistEntity] row is inserted.
 *  3. The Bloom Filter is atomically recompiled.
 *
 * The whitelist takes precedence over static, ML, and any future rule sources.
 *
 * Table: `whitelist`
 */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
