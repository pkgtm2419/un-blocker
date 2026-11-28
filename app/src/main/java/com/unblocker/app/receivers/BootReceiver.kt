package com.unblocker.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.BootPolicy
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.UnblockerVpnService

/**
 * Requests VPN restart after boot or app update when stored user intent and existing consent permit it.
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * Handles boot and package-replacement broadcasts and schedules health work after an eligible restart.
     *
     * @param context Broadcast context for settings and service startup.
     * @param intent Received broadcast, or null; unrelated actions are ignored.
     */
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action
        if (!BootPolicy.isEligibleAction(action)) return

        val preferences = FilteringPreferences.getInstance(context)
        val quickStartManager = QuickStartManager.getInstance(context)
        handleRestart(
            action = action,
            autoRestartOnBoot = preferences.autoRestartOnBoot.value,
            protectionEnabled = preferences.protectionEnabled.value,
            hasVpnPermission = quickStartManager.hasVpnPermission(),
            startVpn = { UnblockerVpnService.start(context, fromBackground = true) },
            onStartFailed = { UnblockerVpnService.session.failed() },
            scheduleHealthCheck = { quickStartManager.scheduleHealthCheckWorker() },
        )
    }

    internal fun handleRestart(
        action: String?,
        autoRestartOnBoot: Boolean,
        protectionEnabled: Boolean,
        hasVpnPermission: Boolean,
        startVpn: () -> Unit,
        onStartFailed: () -> Unit,
        scheduleHealthCheck: () -> Unit,
    ): Boolean {
        if (!BootPolicy.isEligibleAction(action)) return false
        if (!BootPolicy.shouldStart(autoRestartOnBoot, protectionEnabled, hasVpnPermission)) {
            return false
        }
        try {
            startVpn()
        } catch (_: Exception) {
            onStartFailed()
        }
        runCatching { scheduleHealthCheck() }
        return true
    }
}
