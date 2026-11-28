package com.unblocker.app.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.TileService
import com.unblocker.app.MainActivity
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.widgets.ProtectionWidgetProvider

/**
 * Pure decision outcome when user clicks a surface control (Tile or Widget).
 */
enum class TileClickDecision {
    REQUEST_CONSENT_IN_APP,
    STOP_PROTECTION,
    START_PROTECTION,
}

/**
 * Coordinates VPN start, stop, pause, resume, and system UI surface refreshes cleanly across
 * Quick Settings Tile (WP-30), Homescreen Widget (WP-32), and Automation Receiver (WP-33).
 */
object VpnControlActions {
    /**
     * Pure state transition used by Tile and Widget clicks so VPN consent is always respected.
     */
    fun resolveToggleDecision(
        hasVpnConsent: Boolean,
        isProtectionActive: Boolean,
    ): TileClickDecision =
        when {
            !hasVpnConsent -> TileClickDecision.REQUEST_CONSENT_IN_APP
            isProtectionActive -> TileClickDecision.STOP_PROTECTION
            else -> TileClickDecision.START_PROTECTION
        }

    fun hasVpnConsent(context: Context): Boolean = VpnService.prepare(context.applicationContext) == null

    /**
     * Attempts to start protection from a background component if Android VPN consent already exists.
     *
     * @return True when protection start was dispatched, false when consent is missing (and UI launched if requested).
     */
    fun startIfConsented(
        context: Context,
        launchUiOnMissingConsent: Boolean = true,
    ): Boolean {
        val appContext = context.applicationContext
        if (!hasVpnConsent(appContext)) {
            if (launchUiOnMissingConsent) {
                openAppScreen(appContext)
            }
            notifySystemSurfaces(appContext)
            return false
        }
        val prefs = FilteringPreferences.getInstance(appContext)
        prefs.setProtectionEnabled(true)
        UnblockerVpnService.start(appContext, fromBackground = true)
        notifySystemSurfaces(appContext)
        return true
    }

    /**
     * Stops active VPN protection and updates Tile + Widget state.
     */
    fun stopProtection(context: Context) {
        val appContext = context.applicationContext
        UnblockerVpnService.stop(appContext)
        notifySystemSurfaces(appContext)
    }

    /**
     * Toggles five-minute temporary pause state when VPN protection is running.
     */
    fun togglePause(context: Context) {
        val appContext = context.applicationContext
        val prefs = FilteringPreferences.getInstance(appContext)
        if (!UnblockerVpnService.isServiceActive.value) return
        val action =
            if (prefs.isPaused()) {
                UnblockerVpnService.ACTION_RESUME_PROTECTION
            } else {
                UnblockerVpnService.ACTION_PAUSE_PROTECTION
            }
        runCatching {
            appContext.startService(
                Intent(appContext, UnblockerVpnService::class.java).apply { this.action = action },
            )
        }
        notifySystemSurfaces(appContext)
    }

    /**
     * Explicitly pauses or resumes protection from automation / system triggers.
     */
    fun setPaused(
        context: Context,
        pause: Boolean,
    ) {
        val appContext = context.applicationContext
        if (!UnblockerVpnService.isServiceActive.value) return
        val action =
            if (pause) {
                UnblockerVpnService.ACTION_PAUSE_PROTECTION
            } else {
                UnblockerVpnService.ACTION_RESUME_PROTECTION
            }
        runCatching {
            appContext.startService(
                Intent(appContext, UnblockerVpnService::class.java).apply { this.action = action },
            )
        }
        notifySystemSurfaces(appContext)
    }

    fun openAppScreen(context: Context) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Requests immediate refresh for both Quick Settings Tile (API 24+) and installed Homescreen Widgets.
     */
    fun notifySystemSurfaces(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            TileService.requestListeningState(
                appContext,
                ComponentName(appContext, UnblockerTileService::class.java),
            )
        }
        ProtectionWidgetProvider.requestRefresh(appContext)
    }
}
