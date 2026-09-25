package com.unblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.app.data.model.ConnectionLog
import kotlinx.coroutines.flow.Flow

data class DomainCount(
    val domain: String,
    val count: Int
)

data class AppCount(
    val source_app: String,
    val count: Int
)

@Dao
interface ConnectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ConnectionLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ConnectionLog>)

    @Query("SELECT * FROM connections ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogsFlow(limit: Int = 300): Flow<List<ConnectionLog>>

    @Query("""
        SELECT * FROM connections 
        WHERE (:searchQuery = '' OR domain LIKE '%' || :searchQuery || '%' OR source_app LIKE '%' || :searchQuery || '%')
        AND (:category IS NULL OR category = :category)
        AND (:isBlocked IS NULL OR is_blocked = :isBlocked)
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getFilteredLogsFlow(
        searchQuery: String = "",
        category: String? = null,
        isBlocked: Boolean? = null,
        limit: Int = 300
    ): Flow<List<ConnectionLog>>

    @Query("SELECT COUNT(*) FROM connections")
    fun countTotalConnectionsFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1")
    fun countTotalBlockedFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1 AND category = 'AD'")
    fun countTotalAdsBlockedFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1 AND category = 'ADULT_CONTENT'")
    fun countTotalAdultBlockedFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1 AND category = 'AD' AND timestamp >= :sinceTimestamp")
    fun countAdsBlockedSinceFlow(sinceTimestamp: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1 AND category = 'ADULT_CONTENT' AND timestamp >= :sinceTimestamp")
    fun countAdultBlockedSinceFlow(sinceTimestamp: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM connections WHERE timestamp >= :sinceTimestamp")
    fun countConnectionsSinceFlow(sinceTimestamp: Long): Flow<Long>

    @Query("""
        SELECT domain, COUNT(*) as count 
        FROM connections 
        WHERE is_blocked = 1 AND category = 'AD'
        GROUP BY domain 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    fun getTopBlockedAdDomainsFlow(limit: Int = 5): Flow<List<DomainCount>>

    @Query("""
        SELECT domain, COUNT(*) as count 
        FROM connections 
        WHERE is_blocked = 1 AND category = 'ADULT_CONTENT'
        GROUP BY domain 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    fun getTopBlockedAdultDomainsFlow(limit: Int = 5): Flow<List<DomainCount>>

    @Query("""
        SELECT source_app, COUNT(*) as count 
        FROM connections 
        GROUP BY source_app 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    fun getTopAppsFlow(limit: Int = 5): Flow<List<AppCount>>

    @Query("DELETE FROM connections WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long): Int

    @Query("DELETE FROM connections")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM connections")
    suspend fun countTotalConnections(): Long

    @Query("SELECT COUNT(*) FROM connections WHERE is_blocked = 1")
    suspend fun countTotalBlocked(): Long
}
