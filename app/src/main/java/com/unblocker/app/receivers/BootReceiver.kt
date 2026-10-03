package com.unblocker.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.UnblockerVpnService
import com.unblocker.app.services.BootPolicy

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            val preferences = FilteringPreferences.getInstance(context)
            val quickStartManager = QuickStartManager.getInstance(context)
            if (BootPolicy.shouldStart(preferences.autoRestartOnBoot.value,
                    preferences.protectionEnabled.value, quickStartManager.hasVpnPermission())) {
                try {
                    UnblockerVpnService.start(context, fromBackground = true)
                } catch (_: Exception) {
                    UnblockerVpnService.session.failed()
                }
                runCatching { quickStartManager.scheduleHealthCheckWorker() }
            }
        }
    }
}
