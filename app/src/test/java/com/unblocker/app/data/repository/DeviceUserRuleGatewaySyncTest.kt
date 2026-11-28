package com.unblocker.app.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.ui.exceptions.ExceptionTab
import com.unblocker.app.ui.exceptions.ExceptionsViewModel
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.entity.UserRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies dual-write sync between Keystore HMAC sets and the readable `user_rule` Room table (WP-20 / D2).
 */
class DeviceUserRuleGatewaySyncTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @After
    fun tearDown() {
        DeviceLearning.clearForTesting()
    }

    @Test
    fun setWholeSiteAndResetSynchronizeReadableUserRuleTableAndHmacSets() =
        runTest {
            DeviceLearning.clearForTesting()
            val filesDir = tempFolder.newFolder("files")
            val noBackupDir = tempFolder.newFolder("no_backup")
            val fakeContext =
                object : ContextWrapper(null) {
                    override fun getApplicationContext(): Context = this

                    override fun getFilesDir(): File = filesDir

                    override fun getNoBackupFilesDir(): File = noBackupDir

                    override fun getSharedPreferences(
                        name: String?,
                        mode: Int,
                    ): SharedPreferences = EmptySharedPreferences()
                }
            val fakeDao = FakeUserRuleDao()
            val gateway = DeviceUserRuleGateway(fakeContext, fakeDao, PublicSuffixRules.fallback)

            val allowRes = gateway.set("Ads.Example.com", UserFeedback.ALLOW)
            assertEquals(RuleUpdateResult.Applied, allowRes)
            assertTrue(DeviceLearning.allowlist(fakeContext).contains("ads.example.com"))
            val allowRow = fakeDao.getByDomain("ads.example.com")
            assertEquals(UserRuleEntity.ACTION_ALLOW, allowRow?.action)
            assertFalse(allowRow?.isWholeSite ?: true)

            val blockRes = gateway.setWholeSite("sub.tracker.org", UserFeedback.BLOCK)
            assertEquals(RuleUpdateResult.Applied, blockRes)
            assertTrue(DeviceLearning.blocklist(fakeContext).contains("a.tracker.org"))
            val blockRow = fakeDao.getByDomain("tracker.org")
            assertEquals(UserRuleEntity.ACTION_BLOCK, blockRow?.action)
            assertTrue(blockRow?.isWholeSite ?: false)

            val invalid = gateway.set("invalid domain!", UserFeedback.ALLOW)
            assertTrue(invalid is RuleUpdateResult.Failed)

            assertEquals(
                1,
                ExceptionsViewModel.filterRuleList(fakeDao.getAll(), "ads").size,
            )
            assertEquals(ExceptionTab.ALLOW, ExceptionTab.ALLOW)

            val resetRes = gateway.reset("sub.tracker.org")
            assertEquals(RuleUpdateResult.Applied, resetRes)
            assertFalse(DeviceLearning.blocklist(fakeContext).contains("tracker.org"))
            assertEquals(null, fakeDao.getByDomain("tracker.org"))
        }

    private class FakeUserRuleDao : UserRuleDao {
        private val items = MutableStateFlow<List<UserRuleEntity>>(emptyList())

        override fun observeAll(): Flow<List<UserRuleEntity>> = items

        override fun observeByAction(action: Int): Flow<List<UserRuleEntity>> =
            items.map { list -> list.filter { it.action == action } }

        override suspend fun getByDomain(domain: String): UserRuleEntity? =
            items.value.firstOrNull { it.domain == domain }

        override suspend fun getAll(): List<UserRuleEntity> = items.value

        override suspend fun upsert(entity: UserRuleEntity) {
            val next = items.value.filterNot { it.domain == entity.domain } + entity
            items.value = next.sortedByDescending { it.updatedAt }
        }

        override suspend fun deleteByDomain(domain: String) {
            items.value = items.value.filterNot { it.domain == domain }
        }

        override suspend fun clearAll() {
            items.value = emptyList()
        }
    }

    private class EmptySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = defValue

        override fun contains(key: String?): Boolean = false

        override fun edit(): SharedPreferences.Editor =
            object : SharedPreferences.Editor {
                override fun putString(
                    key: String?,
                    value: String?,
                ) = this

                override fun putStringSet(
                    key: String?,
                    values: MutableSet<String>?,
                ) = apply { if (key != null) data[key] = values }

                override fun putInt(
                    key: String?,
                    value: Int,
                ) = this

                override fun putLong(
                    key: String?,
                    value: Long,
                ) = this

                override fun putFloat(
                    key: String?,
                    value: Float,
                ) = this

                override fun putBoolean(
                    key: String?,
                    value: Boolean,
                ) = this

                override fun remove(key: String?) = this

                override fun clear() = this

                override fun commit(): Boolean = true

                override fun apply() {}
            }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
