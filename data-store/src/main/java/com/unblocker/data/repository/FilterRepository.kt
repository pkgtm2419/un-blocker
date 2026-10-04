package com.unblocker.data.repository

import com.unblocker.common.model.RuleSource
import com.unblocker.data.cache.BloomFilterManager
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing block rules and whitelist entries.
 *
 * This is the single point of truth for all filtering decisions:
 *  - The ML batch worker calls [insertMlBlockRules] to commit its inferred rules.
 *  - The UI calls [whitelistDomain] / [removeFromWhitelist] for user overrides.
 *  - Each mutation triggers a Bloom Filter rebuild so the VPN engine is always current.
 *
 * ## Bloom Filter rebuild guarantee
 * Every mutating operation ([insertMlBlockRules], [whitelistDomain], [removeFromWhitelist])
 * ends with a [BloomFilterManager.reloadFilterFromDatabase] call, ensuring the in-memory
 * filter is **always consistent** with the database after any write.
 */
@Singleton
class FilterRepository @Inject constructor(
    private val blockRuleDao: BlockRuleDao,
    private val whitelistDao: WhitelistDao,
    private val bloomFilterManager: BloomFilterManager
) {

    // ─── Block Rules ──────────────────────────────────────────────────────────

    /**
     * Bulk-commits ML-inferred block rules and triggers a Bloom Filter hot-swap.
     * Called at the end of each nightly batch worker run.
     *
     * @param rules  List of (domain, confidenceScore) pairs inferred by TFLite.
     */
    suspend fun insertMlBlockRules(rules: List<Pair<String, Float>>) {
        val entities = rules.map { (domain, score) ->
            BlockRuleEntity(
                domain = domain,
                confidenceScore = score,
                source = RuleSource.ML.name
            )
        }
        blockRuleDao.insertRules(entities)
        bloomFilterManager.reloadFilterFromDatabase()
    }

    /**
     * Seeds the block_rules table with static domains from the compiled asset list.
     * Called once at first app startup (or after a factory reset).
     *
     * @param domains  Domains from the compiled DNS asset binary.
     */
    suspend fun seedStaticRules(domains: Collection<String>) {
        val entities = domains.map { domain ->
            BlockRuleEntity(
                domain = domain,
                confidenceScore = 1.0f,
                source = RuleSource.STATIC.name
            )
        }
        blockRuleDao.insertRules(entities)
        bloomFilterManager.reloadFilterFromDatabase()
    }

    /**
     * Reactive flow of all ML-inferred rules, ordered by confidence score.
     * Consumed by the Dashboard screen's "ML Detections" section.
     */
    val mlRulesFlow: Flow<List<BlockRuleEntity>> =
        blockRuleDao.getRulesBySourceFlow(RuleSource.ML.name)

    // ─── Whitelist (User Overrides) ───────────────────────────────────────────

    /**
     * Whitelists [domain] — removes any matching block rule, inserts a whitelist entry,
     * and rebuilds the Bloom Filter so traffic passes immediately.
     *
     * This is the one-tap false-positive reversal flow from the LogViewer screen.
     */
    suspend fun whitelistDomain(domain: String) {
        blockRuleDao.deleteRule(domain)
        whitelistDao.insertEntry(WhitelistEntity(domain = domain))
        bloomFilterManager.reloadFilterFromDatabase()
    }

    /**
     * Removes [domain] from the whitelist, re-enabling any existing block rules for it,
     * and rebuilds the Bloom Filter.
     */
    suspend fun removeFromWhitelist(domain: String) {
        whitelistDao.removeEntry(domain)
        bloomFilterManager.reloadFilterFromDatabase()
    }

    /** Reactive flow of all user whitelist entries for the whitelist management screen. */
    val whitelistFlow: Flow<List<WhitelistEntity>> = whitelistDao.getWhitelistFlow()

    /** Returns true if [domain] is currently whitelisted by the user. */
    suspend fun isWhitelisted(domain: String): Boolean = whitelistDao.isWhitelisted(domain)

    // ─── Stats ────────────────────────────────────────────────────────────────

    /** Returns the count of block rules per [RuleSource] for dashboard statistics. */
    suspend fun ruleCountBySource(source: RuleSource): Int =
        blockRuleDao.countBySource(source.name)
}
