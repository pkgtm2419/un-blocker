package com.unblocker.app.data.export

import android.content.Context
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.data.preferences.MyDomainsStore
import com.unblocker.app.data.repository.ExtensionRepository
import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.entity.UserRuleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summary counts returned after restoring a validated settings backup.
 */
data class BackupRestoreSummary(
    val restoredRulesCount: Int,
    val rejectedRulesCount: Int,
    val restoredMyDomainsCount: Int,
)

/**
 * Orchestrates user-controlled SAF export and validated restore of non-secret settings, extensions, and readable rules.
 */
@Singleton
class SettingsBackupManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val userRuleDao: UserRuleDao,
        private val userRuleGateway: UserRuleGateway,
    ) {
        private val preferences = FilteringPreferences.getInstance(context)
        private val extensionRepository = ExtensionRepository.getInstance(context)
        private val myDomainsStore = MyDomainsStore.getInstance(context)

        /**
         * Exports current preferences, extension toggles/custom URLs, readable `user_rule` rows, and My domains as JSON.
         */
        suspend fun exportBackup(outputStream: OutputStream) =
            withContext(Dispatchers.IO) {
                val allExtensions = extensionRepository.extensions.value
                val enabledIds = allExtensions.filter { it.isEnabled && !it.isCustom }.mapTo(linkedSetOf()) { it.id }
                val customExtensions =
                    allExtensions
                        .filter { it.isCustom }
                        .map { CustomExtensionBackup(it.id, it.name, it.url, it.isEnabled) }
                val rules =
                    userRuleDao.getAll().map {
                        UserRuleBackup(domain = it.domain, action = it.action, isWholeSite = it.isWholeSite)
                    }
                val snapshot =
                    SettingsBackupSnapshot(
                        adBlockingEnabled = preferences.adBlockingEnabled.value,
                        adultBlockingEnabled = preferences.adultBlockingEnabled.value,
                        blockingMode = preferences.blockingMode.value,
                        filteringTier = preferences.filteringTier.value,
                        autoRestartOnBoot = preferences.autoRestartOnBoot.value,
                        healthCheckIntervalMinutes = preferences.healthCheckIntervalMinutes.value,
                        blockAnalyticsBreakage = preferences.blockAnalyticsBreakage.value,
                        isQueryLoggingPaused = preferences.isQueryLoggingPaused.value,
                        enabledExtensionIds = enabledIds,
                        customExtensions = customExtensions,
                        userRules = rules,
                        myDomains = myDomainsStore.domains.value.sorted(),
                        excludedPackages = preferences.excludedPackages.value,
                    )
                val encoded = SettingsBackupCodec.encode(snapshot)
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(encoded)
                    writer.flush()
                }
            }

        /**
         * Reads, validates, and applies a backup snapshot without altering Keystore keys or query logs.
         */
        suspend fun restoreBackup(inputStream: InputStream): BackupRestoreSummary =
            withContext(Dispatchers.IO) {
                val snapshot = SettingsBackupCodec.decode(inputStream)

                preferences.setAdBlockingEnabled(snapshot.adBlockingEnabled)
                preferences.setAdultBlockingEnabled(snapshot.adultBlockingEnabled)
                preferences.setBlockingMode(snapshot.blockingMode)
                preferences.setFilteringTier(snapshot.filteringTier)
                preferences.setAutoRestartOnBoot(snapshot.autoRestartOnBoot)
                preferences.setHealthCheckIntervalMinutes(snapshot.healthCheckIntervalMinutes)
                preferences.setBlockAnalyticsBreakage(snapshot.blockAnalyticsBreakage)
                preferences.setQueryLoggingPaused(snapshot.isQueryLoggingPaused)
                preferences.setExcludedPackages(snapshot.excludedPackages)

                extensionRepository.restoreSelection(snapshot.enabledExtensionIds, snapshot.customExtensions)

                var appliedRules = 0
                var rejectedRules = 0
                for (rule in snapshot.userRules) {
                    val feedback =
                        if (rule.action == UserRuleEntity.ACTION_BLOCK) UserFeedback.BLOCK else UserFeedback.ALLOW
                    val res =
                        if (rule.isWholeSite) {
                            userRuleGateway.setWholeSite(rule.domain, feedback)
                        } else {
                            userRuleGateway.set(rule.domain, feedback)
                        }
                    if (res is RuleUpdateResult.Applied) appliedRules++ else rejectedRules++
                }

                var restoredProtected = 0
                for (domain in snapshot.myDomains) {
                    if (userRuleGateway.protectDomain(domain) is RuleUpdateResult.Applied) {
                        restoredProtected++
                    }
                }

                BackupRestoreSummary(
                    restoredRulesCount = appliedRules,
                    rejectedRulesCount = rejectedRules,
                    restoredMyDomainsCount = restoredProtected,
                )
            }
    }
