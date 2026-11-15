package com.unblocker.app.data.repository

import android.content.Context
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of moving legacy Room user rules into the private rule store.
 */
sealed interface MigrationResult {
    /**
     * All discovered legacy rules were migrated.
     *
     * @property migratedRulesCount Number of legacy rows saved privately and removed from Room in this attempt.
     */
    data class Success(val migratedRulesCount: Int) : MigrationResult
    /**
     * The completion preference already marks the legacy migration as finished.
     */
    data object NotRequired : MigrationResult
    /**
     * At least one migration operation failed.
     *
     * @property error Explanation of the most recently reported failure.
     */
    data class Failed(val error: String) : MigrationResult
}

/**
 * Migrates legacy Room allow and user-block rows, removing each row only after its private write succeeds.
 *
 * @param context Application context for the migration completion preference.
 * @param whitelistDao Source of legacy allow rules.
 * @param blockRuleDao Source of legacy user block rules.
 * @param userRuleGateway Destination for private exact-domain overrides.
 */
@Singleton
class LegacyUserRuleMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whitelistDao: WhitelistDao,
    private val blockRuleDao: BlockRuleDao,
    private val userRuleGateway: UserRuleGateway
) {
    /**
     * Private keys for tracking completion of the legacy rule migration.
     */
    companion object {
        private const val PREFS_NAME = "unblocker_migration_state"
        private const val KEY_MIGRATED = "legacy_user_rules_migrated_v1"
    }

    /**
     * Attempts the migration unless its completion preference is set. Partial successes remain migrated.
     *
     * @return Completion, prior completion, or a reported failure; failed rows remain eligible for retry.
     */
    suspend fun migrateIfRequired(): MigrationResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return@withContext MigrationResult.NotRequired
        }

        try {
            var migratedCount = 0
            var anyFailed = false
            var failureReason: String? = null

            // 1. Migrate Room Whitelist entries to private user allow rules
            val whitelisted = whitelistDao.getAllWhitelistedDomains()
            for (domain in whitelisted) {
                when (val ruleUpdate = userRuleGateway.set(domain, UserFeedback.ALLOW)) {
                    is RuleUpdateResult.Applied -> {
                        whitelistDao.removeEntry(domain)
                        migratedCount++
                    }
                    is RuleUpdateResult.Failed -> {
                        anyFailed = true
                        failureReason = ruleUpdate.reason
                    }
                }
            }

            // 2. Migrate Room USER block rules to private user block rules
            val userBlockRules = blockRuleDao.getRulesBySource("USER")
            for (rule in userBlockRules) {
                when (val ruleUpdate = userRuleGateway.set(rule.domain, UserFeedback.BLOCK)) {
                    is RuleUpdateResult.Applied -> {
                        blockRuleDao.deleteRule(rule.domain)
                        migratedCount++
                    }
                    is RuleUpdateResult.Failed -> {
                        anyFailed = true
                        failureReason = ruleUpdate.reason
                    }
                }
            }

            if (anyFailed) {
                return@withContext MigrationResult.Failed(failureReason ?: "Some rules failed to migrate")
            }

            // 3. Mark migration complete only after all private writes were confirmed
            prefs.edit().putBoolean(KEY_MIGRATED, true).commit()

            MigrationResult.Success(migratedCount)
        } catch (e: Exception) {
            MigrationResult.Failed(e.message ?: "Unknown migration error")
        }
    }
}
