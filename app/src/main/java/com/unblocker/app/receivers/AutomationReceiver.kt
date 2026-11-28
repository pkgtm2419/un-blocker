package com.unblocker.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.VpnControlActions

/**
 * Opt-in token-authenticated broadcast endpoint for local automation apps (WP-33 / F37).
 *
 * Disabled by default. When enabled in Settings -> Automation, callers must pass extra `"token"`
 * matching [FilteringPreferences.automationToken]. Requests without valid consent are ignored.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent == null) return
        val prefs = FilteringPreferences.getInstance(context)
        val command =
            AutomationCommandPolicy.authorize(
                automationEnabled = prefs.automationEnabled.value,
                storedToken = prefs.automationToken.value,
                suppliedToken = intent.getStringExtra(AutomationCommandPolicy.EXTRA_TOKEN),
                action = intent.action,
            ) ?: return

        when (command) {
            AutomationCommand.ENABLE -> VpnControlActions.startIfConsented(context, launchUiOnMissingConsent = false)
            AutomationCommand.DISABLE -> VpnControlActions.stopProtection(context)
            AutomationCommand.PAUSE -> VpnControlActions.setPaused(context, pause = true)
            AutomationCommand.RESUME -> VpnControlActions.setPaused(context, pause = false)
        }
    }
}
