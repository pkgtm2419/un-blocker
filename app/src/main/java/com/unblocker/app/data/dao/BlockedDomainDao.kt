package com.unblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.app.data.model.BlockedDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: BlockedDomain): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(domains: List<BlockedDomain>)

    @Delete
    suspend fun delete(domain: BlockedDomain)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("SELECT * FROM blocked_domains ORDER BY added_date DESC")
    fun getAllFlow(): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains")
    suspend fun getAll(): List<BlockedDomain>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :domain LIMIT 1)")
    suspend fun isBlocked(domain: String): Boolean

    @Query("SELECT COUNT(*) FROM blocked_domains")
    fun countFlow(): Flow<Int>
}
