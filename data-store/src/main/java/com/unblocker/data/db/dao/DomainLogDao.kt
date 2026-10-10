package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for domain aggregated statistics and bounded event history.
 */
@Dao
interface DomainLogDao {

    @Query("""
        INSERT INTO domain_stats (domain, call_count, blocked_count, allowed_count, first_seen, last_seen, last_decision, last_reason)
        VALUES (:domain, :callDelta, :blockedDelta, :allowedDelta, :ts, :ts, :lastDecision, :lastReason)
        ON CONFLICT(domain) DO UPDATE SET
            call_count = call_count + :callDelta,
            blocked_count = blocked_count + :blockedDelta,
            allowed_count = allowed_count + :allowedDelta,
            last_seen = :ts,
            last_decision = :lastDecision,
            last_reason = :lastReason
    """)
    suspend fun upsertStat(
        domain: String,
        callDelta: Long,
        blockedDelta: Long,
        allowedDelta: Long,
        ts: Long,
        lastDecision: Int,
        lastReason: Int
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DomainEventEntity>)

    @Query("""
        SELECT * FROM domain_stats
        WHERE (:filter = 0 OR (:filter = 1 AND blocked_count > 0) OR (:filter = 2 AND allowed_count > 0))
          AND (:query IS NULL OR domain LIKE :query || '%')
        ORDER BY
            CASE WHEN :sortByCalls = 1 THEN call_count END DESC,
            CASE WHEN :sortByCalls = 0 THEN last_seen END DESC
        LIMIT :limit
    """)
    fun getDomainStatsFlow(
        filter: Int, // 0 = all, 1 = blocked, 2 = allowed
        query: String?,
        sortByCalls: Int, // 1 = sort by call_count, 0 = sort by last_seen
        limit: Int = 200
    ): Flow<List<DomainStatEntity>>

    @Query("SELECT * FROM domain_events WHERE domain = :domain ORDER BY ts DESC LIMIT :limit")
    suspend fun getEventsForDomain(domain: String, limit: Int = 50): List<DomainEventEntity>

    @Query("SELECT COUNT(*) FROM domain_stats")
    fun getTotalDomainsCountFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(call_count), 0) FROM domain_stats")
    fun getTotalCallsCountFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(blocked_count), 0) FROM domain_stats")
    fun getTotalBlockedCountFlow(): Flow<Long>

    @Query("DELETE FROM domain_events WHERE ts < :cutoffTimestamp")
    suspend fun pruneEventsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM domain_events WHERE id NOT IN (SELECT id FROM domain_events ORDER BY ts DESC LIMIT :globalCap)")
    suspend fun pruneOldestEventsBeyondCap(globalCap: Int = 100_000)

    @Query("DELETE FROM domain_events")
    suspend fun clearEvents()

    @Query("DELETE FROM domain_stats")
    suspend fun clearStats()

    @Transaction
    suspend fun clearAll() {
        clearEvents()
        clearStats()
    }
}
