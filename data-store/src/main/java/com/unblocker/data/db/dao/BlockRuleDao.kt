package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.BlockRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access to block-rule rows. Source labels and whitelist exclusion are caller policies.
 */
@Dao
interface BlockRuleDao {
    /**
     * Replaces rows with matching domain keys in one bulk insert.
     *
     * @param rules Complete replacement rows, including confidence, creation time, and source.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<BlockRuleEntity>)

    /**
     * Inserts or replaces a block-rule row.
     *
     * @param rule Complete row to persist under its domain key.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRuleEntity)

    /**
     * Reads all block-rule domain keys without whitelist exclusion.
     *
     * @return Stored domain strings in unspecified order.
     */
    @Query("SELECT domain FROM block_rules")
    suspend fun getAllBlockedDomains(): List<String>

    /**
     * Observes rows for one source in descending confidence order.
     *
     * @param source Persisted RuleSource name.
     * @return Flow updated when matching rule rows change.
     */
    @Query("SELECT * FROM block_rules WHERE rule_source = :source ORDER BY confidence_score DESC")
    fun getRulesBySourceFlow(source: String): Flow<List<BlockRuleEntity>>

    /**
     * Reads rows for one source.
     *
     * @param source Persisted RuleSource name.
     * @return Matching rows in unspecified order.
     */
    @Query("SELECT * FROM block_rules WHERE rule_source = :source")
    suspend fun getRulesBySource(source: String): List<BlockRuleEntity>

    /**
     * Deletes rows for one source.
     *
     * @param source Persisted RuleSource name to delete.
     */
    @Query("DELETE FROM block_rules WHERE rule_source = :source")
    suspend fun deleteRulesBySource(source: String)

    /**
     * Deletes the row with this exact domain key.
     *
     * @param domain Stored domain key; no suffix matching is performed.
     */
    @Query("DELETE FROM block_rules WHERE domain = :domain")
    suspend fun deleteRule(domain: String)

    /**
     * Counts rows for a source.
     *
     * @param source Persisted RuleSource name.
     * @return Number of matching rows.
     */
    @Query("SELECT COUNT(*) FROM block_rules WHERE rule_source = :source")
    suspend fun countBySource(source: String): Int

    /**
     * Checks exact block-rule membership without applying allow precedence.
     *
     * @param domain Stored domain key.
     * @return Whether a matching block row exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM block_rules WHERE domain = :domain LIMIT 1)")
    suspend fun isDomainBlocked(domain: String): Boolean

    /**
     * Deletes all rows whose persisted source is ML.
     */
    @Query("DELETE FROM block_rules WHERE rule_source = 'ML'")
    suspend fun clearMlRules()
}
