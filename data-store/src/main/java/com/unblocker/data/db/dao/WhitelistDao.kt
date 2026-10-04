package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `whitelist` table.
 *
 * The whitelist is user-managed and checked at Bloom Filter compile time:
 * any domain present here is excluded from the filter, so it is always allowed through.
 *
 * ## Precedence
 * whitelist > USER rule > ML rule > STATIC rule
 */
@Dao
interface WhitelistDao {

    /** Inserts or replaces a whitelist entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WhitelistEntity)

    /** Removes a domain from the whitelist (re-enables blocking for it). */
    @Query("DELETE FROM whitelist WHERE domain = :domain")
    suspend fun removeEntry(domain: String)

    /** Returns all whitelisted domain strings — used during Bloom Filter compilation. */
    @Query("SELECT domain FROM whitelist ORDER BY added_at DESC")
    suspend fun getAllWhitelistedDomains(): List<String>

    /** Reactive flow of all whitelist entries for the whitelist management screen. */
    @Query("SELECT * FROM whitelist ORDER BY added_at DESC")
    fun getWhitelistFlow(): Flow<List<WhitelistEntity>>

    /** Returns true if [domain] is in the whitelist. */
    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE domain = :domain LIMIT 1)")
    suspend fun isWhitelisted(domain: String): Boolean

    /** Clears the entire whitelist — used for a factory reset. */
    @Query("DELETE FROM whitelist")
    suspend fun clearAll()
}
