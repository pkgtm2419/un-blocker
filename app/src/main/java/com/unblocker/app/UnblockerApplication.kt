package com.unblocker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Initializes application policy, logging, preferences and background-work dependencies.
 */
@HiltAndroidApp
class UnblockerApplication : Application(), Configuration.Provider {

    /**
     * Hilt worker factory used by WorkManager configuration.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Local query-aggregate DAO supplied to the logging sink.
     */
    @Inject
    lateinit var domainLogDao: com.unblocker.data.db.dao.DomainLogDao

    /**
     * Migration launched asynchronously after application startup.
     */
    @Inject
    lateinit var legacyUserRuleMigration: com.unblocker.app.data.repository.LegacyUserRuleMigration

    /**
     * Process preferences initialized during onCreate.
     */
    lateinit var preferences: FilteringPreferences
        private set

    /**
     * VPN configuration coordinator initialized during onCreate.
     */
    lateinit var quickStartManager: QuickStartManager
        private set

    /**
     * WorkManager configuration using the injected Hilt worker factory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Loads protection policy, initializes logging/settings and launches legacy-rule migration.
     */
    override fun onCreate() {
        super.onCreate()
        com.unblocker.app.logic.analysis.NeverBlockPolicy.load(this)
        com.unblocker.app.data.logs.QueryLogSink.init(domainLogDao)

        preferences = FilteringPreferences.getInstance(this)
        com.unblocker.app.data.logs.QueryLogSink.setLoggingPaused(preferences.isQueryLoggingPaused.value)
        quickStartManager = QuickStartManager.getInstance(this)
        quickStartManager.loadDefaultConfiguration()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            legacyUserRuleMigration.migrateIfRequired()
        }
    }
}
