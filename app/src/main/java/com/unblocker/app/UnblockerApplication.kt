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
    lateinit var workScheduler: com.unblocker.ml.worker.WorkScheduler

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
        preferences = FilteringPreferences.getInstance(this)
        quickStartManager = QuickStartManager.getInstance(this)
        quickStartManager.loadDefaultConfiguration()

        workScheduler.scheduleNightlyBatch()
    }
}
