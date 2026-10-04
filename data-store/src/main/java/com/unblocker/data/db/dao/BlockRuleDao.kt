package com.unblocker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unblocker.data.db.entity.BlockRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the `block_rules` table.
 *
 * ## Performance notes
 * - [insertRules] uses `REPLACE` to allow the ML worker to update the confidence score
 *   of an existing rule if it re-encounters a previously flagged domain with a higher score.
 * - [getAllBlockedDomains] is the hot-path query: called only during Bloom Filter rebuild
 *   (nightly batch + user whitelist override). It returns only domain strings to minimize
 *   cursor allocation.
 * - [getBlockedDomainsForSource] allows source-specific queries for diagnostics.
 */
@Dao
interface BlockRuleDao {

    /**
     * Bulk-upserts block rules. `REPLACE` strategy updates the confidence score and
     * `created_at` timestamp if the domain already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<BlockRuleEntity>)

    /** Upserts a single block rule. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockRuleEntity)

    /**
     * Returns all blocked domain strings for Bloom Filter compilation.
     * Excludes domains present in the whitelist table (handled at repository level).
     */
    @Query("SELECT domain FROM block_rules")
    suspend fun getAllBlockedDomains(): List<String>

    /**
     * Returns all block rule entities for a given source, ordered by confidence score.
     * Used by the dashboard to display ML-inferred vs. static rules separately.
     */
    @Query("SELECT * FROM block_rules WHERE rule_source = :source ORDER BY confidence_score DESC")
    fun getRulesBySourceFlow(source: String): Flow<List<BlockRuleEntity>>

    /** Deletes the block rule for [domain] — used when the user whitelists a domain. */
    @Query("DELETE FROM block_rules WHERE domain = :domain")
    suspend fun deleteRule(domain: String)

    /**
     * Returns the count of block rules per source — used for the dashboard stats widget.
     */
    @Query("SELECT COUNT(*) FROM block_rules WHERE rule_source = :source")
    suspend fun countBySource(source: String): Int

    /** Checks whether a specific domain exists in block_rules. */
    @Query("SELECT EXISTS(SELECT 1 FROM block_rules WHERE domain = :domain LIMIT 1)")
    suspend fun isDomainBlocked(domain: String): Boolean

    /** Deletes all block rules of source "ML" — used for a full ML model reset. */
    @Query("DELETE FROM block_rules WHERE rule_source = 'ML'")
    suspend fun clearMlRules()
}
