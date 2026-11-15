package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access to exact readable allow entries. The repository applies these during Bloom rebuilds.
 */
@Dao
interface WhitelistDao {
    /**
     * Inserts or replaces an exact allow entry.
     *
     * @param entry Domain key and creation time to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WhitelistEntity)

    /**
     * Deletes an exact allow entry without adding a block rule.
     *
     * @param domain Stored domain key to remove.
     */
    @Query("DELETE FROM whitelist WHERE domain = :domain")
    suspend fun removeEntry(domain: String)

    /**
     * Reads all allow-entry keys, newest first.
     *
     * @return Stored readable domains ordered by descending addition time.
     */
    @Query("SELECT domain FROM whitelist ORDER BY added_at DESC")
    suspend fun getAllWhitelistedDomains(): List<String>

    /**
     * Observes all allow entries.
     *
     * @return Entries ordered by descending addition time.
     */
    @Query("SELECT * FROM whitelist ORDER BY added_at DESC")
    fun getWhitelistFlow(): Flow<List<WhitelistEntity>>

    /**
     * Checks exact allow-entry membership.
     *
     * @param domain Stored domain key to look up.
     * @return Whether an entry exists for that exact key.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE domain = :domain LIMIT 1)")
    suspend fun isWhitelisted(domain: String): Boolean

    /**
     * Deletes every allow entry.
     */
    @Query("DELETE FROM whitelist")
    suspend fun clearAll()
}
