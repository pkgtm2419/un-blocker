package com.unblocker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Readable user-managed allow or block override stored strictly on-device without backup (WP-20 / D2).
 *
 * Pre-existing HMAC rules and learned domain reputations remain separate in Keystore-backed sets and
 * are never backfilled into this table.
 *
 * @property domain Normalized lowercase domain primary key.
 * @property action Override polarity: [ACTION_ALLOW] or [ACTION_BLOCK].
 * @property isWholeSite Whether this rule covers the registrable domain and all subdomains.
 * @property source Origin workflow (`manual`, `learn`, `logs`, `import`).
 * @property createdAt First creation timestamp in Unix epoch milliseconds.
 * @property updatedAt Last modification timestamp in Unix epoch milliseconds.
 */
@Entity(
    tableName = "user_rule",
    indices = [
        Index(value = ["action", "updated_at"]),
    ],
)
data class UserRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "action")
    val action: Int,
    @ColumnInfo(name = "is_whole_site")
    val isWholeSite: Boolean,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    /**
     * Supported rule action constants stored in [action].
     */
    companion object {
        /** Explicit allow override overriding blocklists. */
        const val ACTION_ALLOW: Int = 1

        /** Explicit user block override. */
        const val ACTION_BLOCK: Int = 2
    }
}
