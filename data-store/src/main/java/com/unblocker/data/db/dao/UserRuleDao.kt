package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.unblocker.data.db.entity.UserRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for user-visible allow and block exceptions stored in the `user_rule` table (WP-20 / D2).
 */
@Dao
interface UserRuleDao {
    /**
     * Streams all readable user exceptions ordered most-recently updated first.
     */
    @Query("SELECT * FROM user_rule ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    /**
     * Streams user exceptions filtered by [action] ordered most-recently updated first.
     */
    @Query("SELECT * FROM user_rule WHERE action = :action ORDER BY updated_at DESC")
    fun observeByAction(action: Int): Flow<List<UserRuleEntity>>

    /**
     * Reads a single exception by exact normalized [domain], if present.
     */
    @Query("SELECT * FROM user_rule WHERE domain = :domain LIMIT 1")
    suspend fun getByDomain(domain: String): UserRuleEntity?

    /**
     * Returns all stored readable user exceptions.
     */
    @Query("SELECT * FROM user_rule ORDER BY updated_at DESC")
    suspend fun getAll(): List<UserRuleEntity>

    /**
     * Inserts or updates one readable exception entry.
     */
    @Upsert
    suspend fun upsert(entity: UserRuleEntity)

    /**
     * Deletes any readable exception matching normalized [domain].
     */
    @Query("DELETE FROM user_rule WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    /**
     * Removes every readable exception from the table.
     */
    @Query("DELETE FROM user_rule")
    suspend fun clearAll()
}
