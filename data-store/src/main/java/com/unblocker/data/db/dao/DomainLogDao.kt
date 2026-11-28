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
 * Counter deltas and latest metadata applied to one domain aggregate.
 *
 * @property domain Readable domain key.
 * @property callDelta Query count to add.
 * @property blockedDelta Blocked query count to add.
 * @property allowedDelta Allowed query count to add.
 * @property ts Supplied latest observation time in epoch milliseconds.
 * @property lastDecision Latest decision code, conventionally 0 allowed or 1 blocked.
 * @property lastReason Latest app-defined reason code.
 */
data class DomainStatUpsert(
    val domain: String,
    val callDelta: Long,
    val blockedDelta: Long,
    val allowedDelta: Long,
    val ts: Long,
    val lastDecision: Int,
    val lastReason: Int,
    val failDelta: Long = 0L,
    val lastOutcome: Int = lastDecision,
    val lastRule: String = "",
    val lastSource: String = "",
    val lastNetworkType: String = "",
)

/**
 * Summary across all stored domain aggregate rows.
 *
 * @property totalDomains Number of distinct stored domains.
 * @property totalCalls Sum of query counts, or zero for an empty table.
 * @property totalBlocked Sum of blocked counts, or zero for an empty table.
 * @property firstObserved Earliest first-seen epoch milliseconds, or null for an empty table.
 * @property lastObserved Latest last-seen epoch milliseconds, or null for an empty table.
 */
data class DomainLogAggregate(
    val totalDomains: Long,
    val totalCalls: Long,
    val totalBlocked: Long,
    val firstObserved: Long?,
    val lastObserved: Long?,
)

/**
 * DAO for domain aggregated statistics and bounded event history.
 */
@Dao
interface DomainLogDao {
    /**
     * Adds counters to an existing domain or inserts its first aggregate row.
     *
     * First-seen time is retained on conflict; latest time and codes are replaced as supplied.
     *
     * @param domain Readable domain key.
     * @param callDelta Query count to add.
     * @param blockedDelta Blocked count to add.
     * @param allowedDelta Allowed count to add.
     * @param ts Latest observation time in epoch milliseconds.
     * @param lastDecision Latest decision code, conventionally 0 allowed or 1 blocked.
     * @param lastReason Latest app-defined reason code.
     */
    @Query(
        """
        INSERT INTO domain_stats (
            domain, call_count, blocked_count, allowed_count, first_seen, last_seen,
            last_decision, last_reason, last_outcome, last_rule, last_source, fail_count, last_network_type
        )
        VALUES (
            :domain, :callDelta, :blockedDelta, :allowedDelta, :ts, :ts,
            :lastDecision, :lastReason, :lastOutcome, :lastRule, :lastSource, :failDelta, :lastNetworkType
        )
        ON CONFLICT(domain) DO UPDATE SET
            call_count = call_count + :callDelta,
            blocked_count = blocked_count + :blockedDelta,
            allowed_count = allowed_count + :allowedDelta,
            last_seen = :ts,
            last_decision = :lastDecision,
            last_reason = :lastReason,
            last_outcome = :lastOutcome,
            last_rule = :lastRule,
            last_source = :lastSource,
            fail_count = fail_count + :failDelta,
            last_network_type = CASE WHEN :lastNetworkType != '' THEN :lastNetworkType ELSE last_network_type END
    """,
    )
    suspend fun upsertStat(
        domain: String,
        callDelta: Long,
        blockedDelta: Long,
        allowedDelta: Long,
        ts: Long,
        lastDecision: Int,
        lastReason: Int,
        failDelta: Long = 0L,
        lastOutcome: Int = lastDecision,
        lastRule: String = "",
        lastSource: String = "",
        lastNetworkType: String = "",
    )

    /**
     * Applies aggregate deltas in one Room transaction.
     *
     * @param stats Domain deltas applied in list order.
     */
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
                lastReason = stat.lastReason,
                failDelta = stat.failDelta,
                lastOutcome = stat.lastOutcome,
                lastRule = stat.lastRule,
                lastSource = stat.lastSource,
                lastNetworkType = stat.lastNetworkType,
            )
        }
    }

    /**
     * Inserts event rows, replacing any conflicting primary keys.
     *
     * @param events Plaintext local query events to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DomainEventEntity>)

    /**
     * Observes matching aggregates ordered by descending query count.
     *
     * @param filter 0 selects all rows, 1 rows with blocks, 2 rows with allows, 3 rows with upstream failures.
     * @param query Nullable domain prefix used by SQLite LIKE, including any caller wildcard characters.
     * @param limit Maximum rows requested from SQLite.
     * @return Matching aggregates updated when the table changes.
     */
    @Query(
        """
        SELECT * FROM domain_stats
        WHERE (
            :filter = 0
            OR (:filter = 1 AND blocked_count > 0)
            OR (:filter = 2 AND allowed_count > 0)
            OR (:filter = 3 AND (fail_count > 0 OR last_outcome = 2))
        )
          AND (:query IS NULL OR domain LIKE :query || '%')
        ORDER BY call_count DESC
        LIMIT :limit
    """,
    )
    fun getDomainStatsByCallsFlow(
        filter: Int,
        query: String?,
        limit: Int = 200,
    ): Flow<List<DomainStatEntity>>

    /**
     * Observes matching aggregates ordered by descending last-seen time.
     *
     * @param filter 0 selects all rows, 1 rows with blocks, 2 rows with allows, 3 rows with upstream failures.
     * @param query Nullable domain prefix used by SQLite LIKE, including any caller wildcard characters.
     * @param limit Maximum rows requested from SQLite.
     * @return Matching aggregates updated when the table changes.
     */
    @Query(
        """
        SELECT * FROM domain_stats
        WHERE (
            :filter = 0
            OR (:filter = 1 AND blocked_count > 0)
            OR (:filter = 2 AND allowed_count > 0)
            OR (:filter = 3 AND (fail_count > 0 OR last_outcome = 2))
        )
          AND (:query IS NULL OR domain LIKE :query || '%')
        ORDER BY last_seen DESC
        LIMIT :limit
    """,
    )
    fun getDomainStatsByRecentFlow(
        filter: Int,
        query: String?,
        limit: Int = 200,
    ): Flow<List<DomainStatEntity>>

    /**
     * Selects a reactive aggregate query and ordering.
     *
     * @param filter 0 selects all rows, 1 rows with blocks, 2 rows with allows.
     * @param query Nullable SQLite LIKE prefix; wildcard characters retain their SQL meaning.
     * @param sortByCalls 1 orders by query count; every other value orders by last-seen time.
     * @param limit Maximum rows requested from SQLite.
     * @return Matching aggregates in the selected descending order.
     */
    fun getDomainStatsFlow(
        filter: Int, // 0 = all, 1 = blocked, 2 = allowed
        query: String?,
        sortByCalls: Int, // 1 = sort by call_count, 0 = sort by last_seen
        limit: Int = 200,
    ): Flow<List<DomainStatEntity>> =
        if (sortByCalls == 1) {
            getDomainStatsByCallsFlow(filter, query, limit)
        } else {
            getDomainStatsByRecentFlow(filter, query, limit)
        }

    /**
     * Observes counts and observation bounds across all domains.
     *
     * @return Summary with zero counts and null times for an empty table.
     */
    @Query(
        """
        SELECT
            COUNT(*) AS totalDomains,
            COALESCE(SUM(call_count), 0) AS totalCalls,
            COALESCE(SUM(blocked_count), 0) AS totalBlocked,
            MIN(first_seen) AS firstObserved,
            MAX(last_seen) AS lastObserved
        FROM domain_stats
    """,
    )
    fun getAggregateStatsFlow(): Flow<DomainLogAggregate>

    /**
     * Reads event history for one exact domain key.
     *
     * @param domain Readable domain key.
     * @param limit Maximum events requested; this does not limit stored events.
     * @return Events ordered by descending observation time.
     */
    @Query("SELECT * FROM domain_events WHERE domain = :domain ORDER BY ts DESC LIMIT :limit")
    suspend fun getEventsForDomain(
        domain: String,
        limit: Int = 50,
    ): List<DomainEventEntity>

    /**
     * Reads every aggregate for caller-controlled export.
     *
     * @return All stored aggregates ordered by descending last-seen time.
     */
    @Query("SELECT * FROM domain_stats ORDER BY last_seen DESC")
    suspend fun getAllStatsForExport(): List<DomainStatEntity>

    /**
     * Observes the number of stored domain aggregates.
     *
     * @return Distinct domain row count, including zero for an empty table.
     */
    @Query("SELECT COUNT(*) FROM domain_stats")
    fun getTotalDomainsCountFlow(): Flow<Long>

    /**
     * Observes the sum of aggregate query counts.
     *
     * @return Total queries, including zero for an empty table.
     */
    @Query("SELECT COALESCE(SUM(call_count), 0) FROM domain_stats")
    fun getTotalCallsCountFlow(): Flow<Long>

    /**
     * Observes the sum of aggregate blocked counts.
     *
     * @return Total blocked queries, including zero for an empty table.
     */
    @Query("SELECT COALESCE(SUM(blocked_count), 0) FROM domain_stats")
    fun getTotalBlockedCountFlow(): Flow<Long>

    /**
     * Observes the earliest first-seen time.
     *
     * @return Epoch milliseconds, or null when no aggregate rows exist.
     */
    @Query("SELECT MIN(first_seen) FROM domain_stats")
    fun getFirstObservedTimeFlow(): Flow<Long?>

    /**
     * Observes the latest last-seen time.
     *
     * @return Epoch milliseconds, or null when no aggregate rows exist.
     */
    @Query("SELECT MAX(last_seen) FROM domain_stats")
    fun getLastObservedTimeFlow(): Flow<Long?>

    /**
     * Deletes events strictly older than the cutoff.
     *
     * @param cutoffTimestamp Earliest retained event time in epoch milliseconds.
     */
    @Query("DELETE FROM domain_events WHERE ts < :cutoffTimestamp")
    suspend fun pruneEventsOlderThan(cutoffTimestamp: Long)

    /**
     * Keeps only the most recent events across all domains.
     *
     * @param globalCap Maximum event rows retained by this maintenance call.
     */
    @Query("DELETE FROM domain_events WHERE id NOT IN (SELECT id FROM domain_events ORDER BY ts DESC LIMIT :globalCap)")
    suspend fun pruneOldestEventsBeyondCap(globalCap: Int = 100_000)

    /**
     * Deletes aggregates whose last observation is strictly older than the cutoff.
     *
     * @param cutoffTimestamp Earliest retained last-seen time in epoch milliseconds.
     */
    @Query("DELETE FROM domain_stats WHERE last_seen < :cutoffTimestamp")
    suspend fun pruneStatsOlderThan(cutoffTimestamp: Long)

    /**
     * Keeps only aggregates with the most recent last-seen times.
     *
     * @param globalCap Maximum domain rows retained by this maintenance call.
     */
    @Query(
        "DELETE FROM domain_stats WHERE domain NOT IN (SELECT domain FROM domain_stats ORDER BY last_seen DESC LIMIT :globalCap)",
    )
    suspend fun pruneOldestStatsBeyondCap(globalCap: Int = 10_000)

    /**
     * Deletes every local domain event.
     */
    @Query("DELETE FROM domain_events")
    suspend fun clearEvents()

    /**
     * Deletes every local domain aggregate.
     */
    @Query("DELETE FROM domain_stats")
    suspend fun clearStats()

    /**
     * Deletes events and aggregates in one Room transaction.
     */
    @Transaction
    suspend fun clearAll() {
        clearEvents()
        clearStats()
    }
}
