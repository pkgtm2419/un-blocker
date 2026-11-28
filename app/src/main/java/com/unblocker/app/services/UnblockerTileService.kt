package com.unblocker.app.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.unblocker.app.MainActivity
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences

/**
 * Android Quick Settings tile toggling Un-Blocker DNS protection (WP-30 / F34).
 *
 * Respects platform VPN consent boundaries: when [android.net.VpnService.prepare] requires user
 * authorization, tapping opens [MainActivity] rather than faking an active tile state.
 */
class UnblockerTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val hasConsent = VpnControlActions.hasVpnConsent(this)
        val isActive = UnblockerVpnService.isServiceActive.value
        when (VpnControlActions.resolveToggleDecision(hasConsent, isActive)) {
            TileClickDecision.REQUEST_CONSENT_IN_APP -> launchConsentActivity()
            TileClickDecision.STOP_PROTECTION -> VpnControlActions.stopProtection(this)
            TileClickDecision.START_PROTECTION -> VpnControlActions.startIfConsented(this, launchUiOnMissingConsent = false)
        }
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val hasConsent = VpnControlActions.hasVpnConsent(this)
        val status = UnblockerVpnService.session.status.value
        val isPaused = FilteringPreferences.getInstance(this).isPaused()
        val isRunning = status == ServiceStatus.RUNNING || status == ServiceStatus.STARTING

        tile.state =
            when {
                !hasConsent -> Tile.STATE_INACTIVE
                isRunning -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_blocker_notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                when {
                    !hasConsent -> getString(R.string.qs_tile_subtitle_setup)
                    isRunning && isPaused -> getString(R.string.qs_tile_subtitle_paused)
                    isRunning -> getString(R.string.qs_tile_subtitle_active)
                    else -> getString(R.string.qs_tile_subtitle_off)
                }
        }
        tile.updateTile()
    }

    private fun launchConsentActivity() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_TILE_CONSENT,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            startLegacyActivityAndCollapse(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startLegacyActivityAndCollapse(intent: Intent) {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }

    private companion object {
        const val REQUEST_CODE_TILE_CONSENT = 401
    }
}
