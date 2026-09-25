package com.unblocker.app

import android.app.Application
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager

class UnblockerApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: FilteringPreferences
        private set

    lateinit var quickStartManager: QuickStartManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        preferences = FilteringPreferences.getInstance(this)
        quickStartManager = QuickStartManager.getInstance(this)
        quickStartManager.loadDefaultConfiguration()
    }
}
