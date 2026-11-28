package com.unblocker.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.unblocker.app.MainActivity
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.services.TileClickDecision
import com.unblocker.app.services.UnblockerVpnService
import com.unblocker.app.services.VpnControlActions

/**
 * Standard Android [RemoteViews] homescreen widget for live status display, toggle, and 5-minute pause (WP-32 / F36).
 */
class ProtectionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context))
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TOGGLE -> {
                val decision =
                    VpnControlActions.resolveToggleDecision(
                        hasVpnConsent = VpnControlActions.hasVpnConsent(context),
                        isProtectionActive = UnblockerVpnService.isServiceActive.value,
                    )
                when (decision) {
                    TileClickDecision.REQUEST_CONSENT_IN_APP -> {
                        VpnControlActions.openAppScreen(context)
                    }

                    TileClickDecision.STOP_PROTECTION -> {
                        VpnControlActions.stopProtection(context)
                    }

                    TileClickDecision.START_PROTECTION -> {
                        VpnControlActions.startIfConsented(context, launchUiOnMissingConsent = true)
                    }
                }
                requestRefresh(context)
            }

            ACTION_WIDGET_PAUSE_RESUME -> {
                VpnControlActions.togglePause(context)
                requestRefresh(context)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.unblocker.app.widget.TOGGLE"
        const val ACTION_WIDGET_PAUSE_RESUME = "com.unblocker.app.widget.PAUSE_RESUME"

        /**
         * Re-renders all active widget instances from current protection and pause state.
         */
        fun requestRefresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, ProtectionWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views = buildViews(appContext)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_protection)
            val status = UnblockerVpnService.session.status.value
            val isPaused = FilteringPreferences.getInstance(context).isPaused()
            val isActive = status == ServiceStatus.RUNNING || status == ServiceStatus.STARTING

            val statusLabel =
                when {
                    isActive && isPaused -> context.getString(R.string.widget_status_paused)
                    isActive -> context.getString(R.string.widget_status_active)
                    else -> context.getString(R.string.widget_status_stopped)
                }
            val toggleLabel =
                if (isActive) {
                    context.getString(R.string.widget_action_stop)
                } else {
                    context.getString(R.string.widget_action_start)
                }
            val pauseLabel =
                if (isPaused) {
                    context.getString(R.string.notif_action_resume)
                } else {
                    context.getString(R.string.notif_action_pause_5m)
                }

            views.setTextViewText(R.id.widget_status_text, statusLabel)
            views.setTextViewText(R.id.widget_toggle_button, toggleLabel)
            views.setTextViewText(R.id.widget_pause_button, pauseLabel)

            val launchIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            val openPendingIntent =
                PendingIntent.getActivity(
                    context,
                    501,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

            val toggleIntent =
                Intent(context, ProtectionWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_TOGGLE
                }
            val togglePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    502,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)

            val pauseIntent =
                Intent(context, ProtectionWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_PAUSE_RESUME
                }
            val pausePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    503,
                    pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.widget_pause_button, pausePendingIntent)

            return views
        }
    }
}
