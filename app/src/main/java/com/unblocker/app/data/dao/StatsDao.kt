package com.unblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.app.data.model.ContentFilterStats
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: ContentFilterStats): Long

    @Query("SELECT * FROM content_filter_stats WHERE date = :date LIMIT 1")
    suspend fun getStatsForDate(date: String): ContentFilterStats?

    @Query("SELECT * FROM content_filter_stats ORDER BY date DESC LIMIT 30")
    fun getRecentStatsFlow(): Flow<List<ContentFilterStats>>

    @Query("""
        UPDATE content_filter_stats 
        SET total_connections = total_connections + 1,
            ad_connections = ad_connections + :isAd,
            adult_connections = adult_connections + :isAdult,
            blocked_ads = blocked_ads + :isBlockedAd,
            blocked_adult = blocked_adult + :isBlockedAdult,
            allowed_connections = allowed_connections + :isAllowed,
            data_saved_bytes = data_saved_bytes + :dataSaved
        WHERE date = :date
    """)
    suspend fun incrementStats(
        date: String,
        isAd: Int,
        isAdult: Int,
        isBlockedAd: Int,
        isBlockedAdult: Int,
        isAllowed: Int,
        dataSaved: Long
    ): Int
}
