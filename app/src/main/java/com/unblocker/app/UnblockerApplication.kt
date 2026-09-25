package com.unblocker.app

import android.app.Application
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager

class UnblockerApplication : Application() {

    lateinit var preferences: FilteringPreferences
        private set

    lateinit var quickStartManager: QuickStartManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = FilteringPreferences.getInstance(this)
        quickStartManager = QuickStartManager.getInstance(this)
        quickStartManager.loadDefaultConfiguration()
    }
}
