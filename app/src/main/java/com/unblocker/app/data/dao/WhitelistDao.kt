package com.unblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.app.data.model.WhitelistDomain
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: WhitelistDomain): Long

    @Delete
    suspend fun delete(domain: WhitelistDomain)

    @Query("DELETE FROM whitelist_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("SELECT * FROM whitelist_domains ORDER BY added_date DESC")
    fun getAllFlow(): Flow<List<WhitelistDomain>>

    @Query("SELECT * FROM whitelist_domains")
    suspend fun getAll(): List<WhitelistDomain>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelist_domains WHERE domain = :domain LIMIT 1)")
    suspend fun isWhitelisted(domain: String): Boolean

    @Query("SELECT COUNT(*) FROM whitelist_domains")
    fun countFlow(): Flow<Int>
}
