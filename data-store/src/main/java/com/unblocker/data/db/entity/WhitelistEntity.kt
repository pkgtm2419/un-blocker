package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Readable user allow entry consulted by the library filter repository.
 *
 * The repository deletes a matching block-rule row when adding an allow entry. This entity
 * alone implements neither precedence nor the active app's HMAC rule storage.
 *
 * @property domain Readable domain primary key.
 * @property addedAt Entry creation time in milliseconds since the Unix epoch.
 */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
