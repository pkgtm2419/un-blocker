package com.unblocker.data.repository

import android.content.Context
import com.unblocker.common.model.RuleSource
import com.unblocker.data.cache.BloomFilterManager
import com.unblocker.data.cache.OffHeapRuleEngine
import com.unblocker.data.cache.RuleCompiler
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.WhitelistEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing block rules and whitelist entries.
 *
 * This is the single point of truth for all filtering decisions:
 *  - The ML batch worker calls [insertMlBlockRules] to commit its inferred rules.
 *  - The UI calls [whitelistDomain] / [removeFromWhitelist] for user overrides.
 *  - Each mutation triggers both a Bloom Filter rebuild and an off-heap binary index rebuild
 *    so the VPN engine is always current.
 */
@Singleton
class FilterRepository @Inject constructor(
    private val blockRuleDao: BlockRuleDao,
    private val whitelistDao: WhitelistDao,
    private val bloomFilterManager: BloomFilterManager,
    private val offHeapRuleEngine: OffHeapRuleEngine,
    @ApplicationContext private val context: Context
) {

    // ─── Block Rules ──────────────────────────────────────────────────────────

    /**
     * Bulk-commits ML-inferred block rules, triggers a Bloom Filter hot-swap,
     * and rebuilds the off-heap binary rule index (`rules.bin`).
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
        rebuildOffHeapIndex()
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
        rebuildOffHeapIndex()
    }

    /**
     * Rebuilds the off-heap binary index (`rules.bin`) from active block rules
     * and reloads [OffHeapRuleEngine] atomically.
     */
    suspend fun rebuildOffHeapIndex() {
        runCatching {
            val domains = blockRuleDao.getAllBlockedDomains()
            val content = domains.joinToString("\n")
            val reader = BufferedReader(StringReader(content))
            val rulesFile = File(context.filesDir, "rules.bin")
            RuleCompiler.compileStreamsToBinaryFile(listOf(reader), rulesFile)
            offHeapRuleEngine.reloadFromFile(rulesFile)
        }
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
     * and rebuilds both the Bloom Filter and the off-heap rule index so traffic passes immediately.
     *
     * This is the one-tap false-positive reversal flow from the LogViewer screen.
     */
    suspend fun whitelistDomain(domain: String) {
        blockRuleDao.deleteRule(domain)
        whitelistDao.insertEntry(WhitelistEntity(domain = domain))
        bloomFilterManager.reloadFilterFromDatabase()
        rebuildOffHeapIndex()
    }

    /**
     * Removes [domain] from the whitelist, re-enabling any existing block rules for it,
     * and rebuilds both filters.
     */
    suspend fun removeFromWhitelist(domain: String) {
        whitelistDao.removeEntry(domain)
        bloomFilterManager.reloadFilterFromDatabase()
        rebuildOffHeapIndex()
    }

    /**
     * User-initiated block override: removes from whitelist if present, inserts a USER block rule,
     * and rebuilds both filters.
     */
    suspend fun blacklistDomain(domain: String) {
        whitelistDao.removeEntry(domain)
        blockRuleDao.insertRule(
            BlockRuleEntity(domain = domain, confidenceScore = 1.0f, source = RuleSource.USER.name)
        )
        bloomFilterManager.reloadFilterFromDatabase()
        rebuildOffHeapIndex()
    }

    /** Returns true if [domain] is currently present in the user whitelist. */
    suspend fun isWhitelisted(domain: String): Boolean = whitelistDao.isWhitelisted(domain)

    /** Returns true if [domain] is blocked in the database. */
    suspend fun isDomainBlocked(domain: String): Boolean = blockRuleDao.isDomainBlocked(domain)

    /** Returns the count of block rules per [RuleSource] for dashboard statistics. */
    suspend fun ruleCountBySource(source: RuleSource): Int =
        blockRuleDao.countBySource(source.name)

    /** Returns the total count of active block rules across all sources. */
    suspend fun countAllBlockRules(): Int =
        blockRuleDao.countBySource(RuleSource.STATIC.name) +
                blockRuleDao.countBySource(RuleSource.ML.name) +
                blockRuleDao.countBySource(RuleSource.USER.name)

    /** Returns the count of ML-inferred rules. */
    suspend fun countMlRules(): Int = blockRuleDao.countBySource(RuleSource.ML.name)

    /** Reactive flow of all whitelisted domains for the UI. */
    val whitelistFlow: Flow<List<WhitelistEntity>> = whitelistDao.getWhitelistFlow()
}
