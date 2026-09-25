package com.unblocker.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.UnblockerVpnService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            val preferences = FilteringPreferences.getInstance(context)
            if (preferences.autoRestartOnBoot.value) {
                val quickStartManager = QuickStartManager.getInstance(context)
                if (quickStartManager.hasVpnPermission()) {
                    UnblockerVpnService.start(context)
                    quickStartManager.scheduleHealthCheckWorker()
                }
            }
        }
    }
}
