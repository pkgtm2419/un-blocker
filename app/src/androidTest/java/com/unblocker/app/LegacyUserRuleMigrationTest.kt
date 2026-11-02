package com.unblocker.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.data.repository.LegacyUserRuleMigration
import com.unblocker.app.data.repository.MigrationResult
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.WhitelistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyUserRuleMigrationTest {

    private class FakeWhitelistDao : WhitelistDao {
        val domains = mutableListOf<String>()
        override suspend fun insertEntry(entry: WhitelistEntity) { domains.add(entry.domain) }
        override suspend fun removeEntry(domain: String) { domains.remove(domain) }
        override suspend fun getAllWhitelistedDomains(): List<String> = domains.toList()
        override fun getWhitelistFlow(): Flow<List<WhitelistEntity>> = flowOf(emptyList())
        override suspend fun isWhitelisted(domain: String): Boolean = domain in domains
        override suspend fun clearAll() { domains.clear() }
    }

    private class FakeBlockRuleDao : BlockRuleDao {
        val rules = mutableListOf<BlockRuleEntity>()
        override suspend fun insertRules(rules: List<BlockRuleEntity>) { this.rules.addAll(rules) }
        override suspend fun insertRule(rule: BlockRuleEntity) { this.rules.add(rule) }
        override suspend fun getAllBlockedDomains(): List<String> = rules.map { it.domain }
        override fun getRulesBySourceFlow(source: String): Flow<List<BlockRuleEntity>> = flowOf(emptyList())
        override suspend fun getRulesBySource(source: String): List<BlockRuleEntity> = rules.filter { it.source == source }
        override suspend fun deleteRulesBySource(source: String) { rules.removeAll { it.source == source } }
        override suspend fun deleteRule(domain: String) { rules.removeAll { it.domain == domain } }
        override suspend fun countBySource(source: String): Int = rules.count { it.source == source }
        override suspend fun isDomainBlocked(domain: String): Boolean = rules.any { it.domain == domain }
        override suspend fun clearMlRules() { rules.removeAll { it.source == "ML" } }
    }

    private class TestGateway : UserRuleGateway {
        val applied = mutableMapOf<String, UserFeedback>()
        override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult {
            applied[domain] = feedback
            return RuleUpdateResult.Applied
        }
    }

    @Test
    fun testMigrateTransfersUserRulesAndCleansLegacyRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("unblocker_migration_state", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val whitelistDao = FakeWhitelistDao()
        whitelistDao.insertEntry(WhitelistEntity("legacy-allow.com"))

        val blockRuleDao = FakeBlockRuleDao()
        blockRuleDao.insertRule(BlockRuleEntity(domain = "legacy-block.com", source = "USER"))
        blockRuleDao.insertRule(BlockRuleEntity(domain = "static-ad.com", source = "STATIC"))

        val gateway = TestGateway()
        val migration = LegacyUserRuleMigration(context, whitelistDao, blockRuleDao, gateway)

        val result = migration.migrateIfRequired()
        assertTrue(result is MigrationResult.Success)
        assertEquals(2, (result as MigrationResult.Success).migratedRulesCount)

        // Verify gateway received the migrated rules
        assertEquals(UserFeedback.ALLOW, gateway.applied["legacy-allow.com"])
        assertEquals(UserFeedback.BLOCK, gateway.applied["legacy-block.com"])

        // Legacy rows removed from Room
        assertEquals(0, whitelistDao.getAllWhitelistedDomains().size)
        assertEquals(0, blockRuleDao.getRulesBySource("USER").size)
        // Non-user STATIC rules preserved
        assertEquals(1, blockRuleDao.rules.size)
        assertEquals("static-ad.com", blockRuleDao.rules[0].domain)

        // Subsequent call returns NotRequired
        val secondResult = migration.migrateIfRequired()
        assertEquals(MigrationResult.NotRequired, secondResult)
    }

    @Test
    fun testPartialFailureLeavesUnmigratedRowsAndDoesNotMarkComplete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("unblocker_migration_state", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val whitelistDao = FakeWhitelistDao()
        whitelistDao.insertEntry(WhitelistEntity("success-allow.com"))
        whitelistDao.insertEntry(WhitelistEntity("fail-allow.com"))

        val blockRuleDao = FakeBlockRuleDao()
        blockRuleDao.insertRule(BlockRuleEntity(domain = "success-block.com", source = "USER"))

        val gateway = object : UserRuleGateway {
            val applied = mutableMapOf<String, UserFeedback>()
            override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult {
                if (domain == "fail-allow.com") return RuleUpdateResult.Failed("Storage full")
                applied[domain] = feedback
                return RuleUpdateResult.Applied
            }
        }

        val migration = LegacyUserRuleMigration(context, whitelistDao, blockRuleDao, gateway)
        val result = migration.migrateIfRequired()

        assertTrue("Expected failure on partial migration", result is MigrationResult.Failed)
        assertEquals("Storage full", (result as MigrationResult.Failed).error)

        // Succeeded entries removed, failed entry remains
        assertEquals(listOf("fail-allow.com"), whitelistDao.getAllWhitelistedDomains())
        assertEquals(0, blockRuleDao.getRulesBySource("USER").size)

        // Migration flag was NOT set
        val isMigrated = prefs.getBoolean("legacy_user_rules_migrated_v1", false)
        assertEquals(false, isMigrated)
    }
}
