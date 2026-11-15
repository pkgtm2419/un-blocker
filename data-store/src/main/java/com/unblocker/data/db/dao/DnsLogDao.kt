package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.DnsLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access to legacy plaintext DNS logs and their per-row analysis state.
 *
 * IGNORE applies to primary-key conflicts; the schema does not deduplicate domain/timestamp pairs.
 */
@Dao
interface DnsLogDao {
    /**
     * Inserts a log row, ignoring a conflicting primary key.
     *
     * @param log Observation to persist.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: DnsLogEntity)

    /**
     * Bulk-inserts log rows, ignoring conflicting primary keys.
     *
     * @param logs Observations to persist.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogs(logs: List<DnsLogEntity>)

    /**
     * Reads unprocessed rows oldest first.
     *
     * @param limit Maximum rows requested from SQLite.
     * @return Unanalyzed rows ordered by ascending observation time.
     */
    @Query("SELECT * FROM dns_logs WHERE is_analyzed = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnanalyzedLogs(limit: Int = 10_000): List<DnsLogEntity>

    /**
     * Marks existing rows as processed.
     *
     * @param logIds Primary keys of rows to update.
     */
    @Query("UPDATE dns_logs SET is_analyzed = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsAnalyzed(logIds: List<Long>)

    /**
     * Observes the 100 most recent stored log rows.
     *
     * @return Rows ordered by descending observation time.
     */
    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<DnsLogEntity>>

    /**
     * Counts unprocessed rows.
     *
     * @return Number of rows whose analysis flag is false.
     */
    @Query("SELECT COUNT(*) FROM dns_logs WHERE is_analyzed = 0")
    suspend fun countUnanalyzedLogs(): Int

    /**
     * Deletes rows strictly older than the cutoff.
     *
     * @param cutoffTimestamp Earliest retained observation time in epoch milliseconds.
     */
    @Query("DELETE FROM dns_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long)

    /**
     * Deletes every legacy DNS log row.
     */
    @Query("DELETE FROM dns_logs")
    suspend fun clearAll()
}
