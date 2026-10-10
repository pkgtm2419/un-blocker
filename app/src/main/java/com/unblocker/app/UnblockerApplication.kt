package com.unblocker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UnblockerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var domainLogDao: com.unblocker.data.db.dao.DomainLogDao

    lateinit var preferences: FilteringPreferences
        private set

    lateinit var quickStartManager: QuickStartManager
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.unblocker.app.logic.analysis.NeverBlockPolicy.load(this)
        com.unblocker.app.data.logs.QueryLogSink.init(domainLogDao)

        preferences = FilteringPreferences.getInstance(this)
        quickStartManager = QuickStartManager.getInstance(this)
        quickStartManager.loadDefaultConfiguration()
    }
}
