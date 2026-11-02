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

sealed interface MigrationResult {
    data class Success(val migratedRulesCount: Int) : MigrationResult
    data object NotRequired : MigrationResult
    data class Failed(val error: String) : MigrationResult
}

@Singleton
class LegacyUserRuleMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whitelistDao: WhitelistDao,
    private val blockRuleDao: BlockRuleDao,
    private val userRuleGateway: UserRuleGateway
) {
    companion object {
        private const val PREFS_NAME = "unblocker_migration_state"
        private const val KEY_MIGRATED = "legacy_user_rules_migrated_v1"
    }

    suspend fun migrateIfRequired(): MigrationResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return@withContext MigrationResult.NotRequired
        }

        try {
            var migratedCount = 0

            // 1. Migrate Room Whitelist entries to private user allow rules
            val whitelisted = whitelistDao.getAllWhitelistedDomains()
            for (domain in whitelisted) {
                val res = userRuleGateway.set(domain, UserFeedback.ALLOW)
                if (res is RuleUpdateResult.Applied) {
                    migratedCount++
                }
            }

            // 2. Migrate Room USER block rules to private user block rules
            val userBlockRules = blockRuleDao.getRulesBySource("USER")
            for (rule in userBlockRules) {
                val res = userRuleGateway.set(rule.domain, UserFeedback.BLOCK)
                if (res is RuleUpdateResult.Applied) {
                    migratedCount++
                }
            }

            // 3. Clear plaintext legacy rows now that private writes are durable
            if (whitelisted.isNotEmpty()) {
                whitelistDao.clearAll()
            }
            if (userBlockRules.isNotEmpty()) {
                blockRuleDao.deleteRulesBySource("USER")
            }

            // 4. Mark migration complete
            prefs.edit().putBoolean(KEY_MIGRATED, true).commit()

            MigrationResult.Success(migratedCount)
        } catch (e: Exception) {
            MigrationResult.Failed(e.message ?: "Unknown migration error")
        }
    }
}
