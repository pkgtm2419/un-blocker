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
 * Coordinates library block-rule and readable allow-entry storage with its in-memory indices.
 *
 * Mutations rebuild the Bloom filter and attempt an off-heap rebuild. This repository is separate
 * from the active app's decision use case and private HMAC rule stores.
 *
 * @param blockRuleDao Persistent block-rule access.
 * @param whitelistDao Persistent exact allow-entry access.
 * @param bloomFilterManager Filter rebuilt after mutations.
 * @param offHeapRuleEngine Mapped index reloaded after successful compilation.
 * @param context Application context supplying the private index directory.
 */
@Singleton
class FilterRepository
    @Inject
    constructor(
        private val blockRuleDao: BlockRuleDao,
        private val whitelistDao: WhitelistDao,
        private val bloomFilterManager: BloomFilterManager,
        private val offHeapRuleEngine: OffHeapRuleEngine,
        @ApplicationContext private val context: Context,
    ) {
        // ─── Block Rules ──────────────────────────────────────────────────────────

        /**
         * Replaces classifier-derived rows, rebuilds the Bloom filter, and attempts an index rebuild.
         *
         * @param rules Domain and confidence pairs; confidence values are persisted without validation.
         */
        suspend fun insertMlBlockRules(rules: List<Pair<String, Float>>) {
            val entities =
                rules.map { (domain, score) ->
                    BlockRuleEntity(
                        domain = domain,
                        confidenceScore = score,
                        source = RuleSource.ML.name,
                    )
                }
            blockRuleDao.insertRules(entities)
            bloomFilterManager.reloadFilterFromDatabase()
            rebuildOffHeapIndex()
        }

        /**
         * Replaces static block-rule rows and refreshes the library indices.
         *
         * @param domains Domain keys supplied by the caller; no startup scheduling is implied.
         */
        suspend fun seedStaticRules(domains: Collection<String>) {
            val entities =
                domains.map { domain ->
                    BlockRuleEntity(
                        domain = domain,
                        confidenceScore = 1.0f,
                        source = RuleSource.STATIC.name,
                    )
                }
            blockRuleDao.insertRules(entities)
            bloomFilterManager.reloadFilterFromDatabase()
            rebuildOffHeapIndex()
        }

        /**
         * Attempts to compile all block-rule rows and reload the mapped index.
         *
         * The compiler also seeds its bootstrap keys. This path does not subtract whitelist keys, and
         * caught compilation/database failures are ignored, potentially retaining the previous index.
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
         * Classifier-source block rows observed in descending confidence order.
         */
        val mlRulesFlow: Flow<List<BlockRuleEntity>> =
            blockRuleDao.getRulesBySourceFlow(RuleSource.ML.name)

        // ─── Whitelist (User Overrides) ───────────────────────────────────────────

        /**
         * Deletes the exact block row, inserts an allow entry, and refreshes the library indices.
         *
         * The writes and rebuilds are sequential, not one database transaction.
         *
         * @param domain Exact readable domain key to allow.
         */
        suspend fun whitelistDomain(domain: String) {
            blockRuleDao.deleteRule(domain)
            whitelistDao.insertEntry(WhitelistEntity(domain = domain))
            bloomFilterManager.reloadFilterFromDatabase()
            rebuildOffHeapIndex()
        }

        /**
         * Removes an exact allow entry and refreshes the indices without restoring deleted block rows.
         *
         * @param domain Readable allow-entry key to remove.
         */
        suspend fun removeFromWhitelist(domain: String) {
            whitelistDao.removeEntry(domain)
            bloomFilterManager.reloadFilterFromDatabase()
            rebuildOffHeapIndex()
        }

        /**
         * Removes an exact allow entry, inserts a USER block row, and refreshes the indices.
         *
         * @param domain Readable domain key to block.
         */
        suspend fun blacklistDomain(domain: String) {
            whitelistDao.removeEntry(domain)
            blockRuleDao.insertRule(
                BlockRuleEntity(domain = domain, confidenceScore = 1.0f, source = RuleSource.USER.name),
            )
            bloomFilterManager.reloadFilterFromDatabase()
            rebuildOffHeapIndex()
        }

        /**
         * Checks exact allow-entry storage.
         *
         * @param domain Readable domain key.
         * @return Whether that exact allow entry exists.
         */
        suspend fun isWhitelisted(domain: String): Boolean = whitelistDao.isWhitelisted(domain)

        /**
         * Checks exact block-row storage without applying allow precedence.
         *
         * @param domain Readable domain key.
         * @return Whether that exact block row exists.
         */
        suspend fun isDomainBlocked(domain: String): Boolean = blockRuleDao.isDomainBlocked(domain)

        /**
         * Counts block rows for one provenance label.
         *
         * @param source Rule source to count.
         * @return Number of matching stored rows.
         */
        suspend fun ruleCountBySource(source: RuleSource): Int = blockRuleDao.countBySource(source.name)

        /**
         * Counts stored rows across STATIC, ML, and USER sources.
         *
         * @return Sum of those three source counts, read through separate queries.
         */
        suspend fun countAllBlockRules(): Int =
            blockRuleDao.countBySource(RuleSource.STATIC.name) +
                blockRuleDao.countBySource(RuleSource.ML.name) +
                blockRuleDao.countBySource(RuleSource.USER.name)

        /**
         * Counts rows labeled ML.
         *
         * @return Number of stored classifier-derived block rows.
         */
        suspend fun countMlRules(): Int = blockRuleDao.countBySource(RuleSource.ML.name)

        /**
         * Readable allow entries observed in descending addition-time order.
         */
        val whitelistFlow: Flow<List<WhitelistEntity>> = whitelistDao.getWhitelistFlow()
    }
