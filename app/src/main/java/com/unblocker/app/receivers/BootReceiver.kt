package com.unblocker.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.UnblockerVpnService
import com.unblocker.app.services.BootPolicy

/**
 * Requests VPN restart after boot when stored user intent and existing consent permit it.
 */
class BootReceiver : BroadcastReceiver() {

    /**
     * Handles boot broadcasts and schedules health work after an eligible restart.
     *
     * @param context Broadcast context for settings and service startup.
     * @param intent Received broadcast, or null; unrelated actions are ignored.
     */
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
