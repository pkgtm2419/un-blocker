package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.DnsLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `dns_logs` table.
 *
 * ## Performance notes
 * - [insertLog] uses `IGNORE` conflict strategy: if the same (domain, timestamp) pair arrives
 *   twice (e.g., a retry burst), the duplicate is silently dropped without a transaction abort.
 * - [getUnanalyzedLogs] is batch-read optimized — it fetches up to [limit] rows in a single
 *   SELECT to minimize I/O round-trips during the nightly ML run.
 * - [markLogsAsAnalyzed] uses an `IN` clause bulk update to avoid per-row transactions.
 * - [pruneOldLogs] keeps the database footprint bounded — called at the end of each nightly run.
 */
@Dao
interface DnsLogDao {

    /** Inserts a single DNS log entry asynchronously from the VPN packet loop. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: DnsLogEntity)

    /**
     * Batch-inserts multiple DNS log entries — used when the VPN engine accumulates
     * a write buffer before flushing (reduces SQLite transaction overhead).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogs(logs: List<DnsLogEntity>)

    /**
     * Returns up to [limit] unanalyzed log entries, oldest-first.
     * The ML worker uses this as its input batch for TFLite inference.
     */
    @Query("SELECT * FROM dns_logs WHERE is_analyzed = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnanalyzedLogs(limit: Int = 10_000): List<DnsLogEntity>

    /**
     * Marks all rows with IDs in [logIds] as analyzed.
     * Called after the ML worker commits its block-rule results.
     */
    @Query("UPDATE dns_logs SET is_analyzed = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsAnalyzed(logIds: List<Long>)

    /**
     * Returns a real-time [Flow] of the 100 most recent DNS log entries.
     * Consumed by the LogViewer Compose screen via a StateFlow.
     */
    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<DnsLogEntity>>

    /**
     * Returns the count of unanalyzed logs — used by the backlog watchdog to trigger
     * a throttled daytime scan when the count exceeds the 5,000-entry threshold.
     */
    @Query("SELECT COUNT(*) FROM dns_logs WHERE is_analyzed = 0")
    suspend fun countUnanalyzedLogs(): Int

    /**
     * Deletes all log entries older than [cutoffTimestamp] (epoch millis).
     * Keeps the database size bounded — typically called with a 7-day cutoff.
     */
    @Query("DELETE FROM dns_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long)

    /** Clears all logs — used for user-triggered data wipe. */
    @Query("DELETE FROM dns_logs")
    suspend fun clearAll()
}
