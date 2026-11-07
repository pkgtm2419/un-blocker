package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import kotlinx.coroutines.flow.Flow

data class DomainStatUpsert(
    val domain: String,
    val callDelta: Long,
    val blockedDelta: Long,
    val allowedDelta: Long,
    val ts: Long,
    val lastDecision: Int,
    val lastReason: Int
)

data class DomainLogAggregate(
    val totalDomains: Long,
    val totalCalls: Long,
    val totalBlocked: Long,
    val firstObserved: Long?,
    val lastObserved: Long?
)

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

    @Transaction
    suspend fun upsertStatsBatch(stats: List<DomainStatUpsert>) {
        for (stat in stats) {
            upsertStat(
                domain = stat.domain,
                callDelta = stat.callDelta,
                blockedDelta = stat.blockedDelta,
                allowedDelta = stat.allowedDelta,
                ts = stat.ts,
                lastDecision = stat.lastDecision,
                lastReason = stat.lastReason
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DomainEventEntity>)

    @Query("""
        SELECT * FROM domain_stats
        WHERE (:filter = 0 OR (:filter = 1 AND blocked_count > 0) OR (:filter = 2 AND allowed_count > 0))
          AND (:query IS NULL OR domain LIKE :query || '%')
        ORDER BY call_count DESC
        LIMIT :limit
    """)
    fun getDomainStatsByCallsFlow(
        filter: Int,
        query: String?,
        limit: Int = 200
    ): Flow<List<DomainStatEntity>>

    @Query("""
        SELECT * FROM domain_stats
        WHERE (:filter = 0 OR (:filter = 1 AND blocked_count > 0) OR (:filter = 2 AND allowed_count > 0))
          AND (:query IS NULL OR domain LIKE :query || '%')
        ORDER BY last_seen DESC
        LIMIT :limit
    """)
    fun getDomainStatsByRecentFlow(
        filter: Int,
        query: String?,
        limit: Int = 200
    ): Flow<List<DomainStatEntity>>

    fun getDomainStatsFlow(
        filter: Int, // 0 = all, 1 = blocked, 2 = allowed
        query: String?,
        sortByCalls: Int, // 1 = sort by call_count, 0 = sort by last_seen
        limit: Int = 200
    ): Flow<List<DomainStatEntity>> =
        if (sortByCalls == 1) getDomainStatsByCallsFlow(filter, query, limit)
        else getDomainStatsByRecentFlow(filter, query, limit)

    @Query("""
        SELECT
            COUNT(*) AS totalDomains,
            COALESCE(SUM(call_count), 0) AS totalCalls,
            COALESCE(SUM(blocked_count), 0) AS totalBlocked,
            MIN(first_seen) AS firstObserved,
            MAX(last_seen) AS lastObserved
        FROM domain_stats
    """)
    fun getAggregateStatsFlow(): Flow<DomainLogAggregate>

    @Query("SELECT * FROM domain_events WHERE domain = :domain ORDER BY ts DESC LIMIT :limit")
    suspend fun getEventsForDomain(domain: String, limit: Int = 50): List<DomainEventEntity>

    @Query("SELECT * FROM domain_stats ORDER BY last_seen DESC")
    suspend fun getAllStatsForExport(): List<DomainStatEntity>

    @Query("SELECT COUNT(*) FROM domain_stats")
    fun getTotalDomainsCountFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(call_count), 0) FROM domain_stats")
    fun getTotalCallsCountFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(blocked_count), 0) FROM domain_stats")
    fun getTotalBlockedCountFlow(): Flow<Long>

    @Query("SELECT MIN(first_seen) FROM domain_stats")
    fun getFirstObservedTimeFlow(): Flow<Long?>

    @Query("SELECT MAX(last_seen) FROM domain_stats")
    fun getLastObservedTimeFlow(): Flow<Long?>

    @Query("DELETE FROM domain_events WHERE ts < :cutoffTimestamp")
    suspend fun pruneEventsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM domain_events WHERE id NOT IN (SELECT id FROM domain_events ORDER BY ts DESC LIMIT :globalCap)")
    suspend fun pruneOldestEventsBeyondCap(globalCap: Int = 100_000)

    @Query("DELETE FROM domain_stats WHERE last_seen < :cutoffTimestamp")
    suspend fun pruneStatsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM domain_stats WHERE domain NOT IN (SELECT domain FROM domain_stats ORDER BY last_seen DESC LIMIT :globalCap)")
    suspend fun pruneOldestStatsBeyondCap(globalCap: Int = 10_000)

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
