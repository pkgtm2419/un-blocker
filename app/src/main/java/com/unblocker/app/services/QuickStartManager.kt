package com.unblocker.app.services

import android.content.Context
import android.net.VpnService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unblocker.app.data.model.DefaultConfig
import com.unblocker.app.data.preferences.FilteringPreferences
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Coordinates VPN consent checks, service commands, default settings and health work.
 *
 * @param context Context reduced to its application context for retained operations.
 */
class QuickStartManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = FilteringPreferences.getInstance(appContext)

    /**
     * Shared VPN lifecycle status emitted by the service session.
     */
    val status: StateFlow<ServiceStatus> = UnblockerVpnService.session.status

    init {
        loadDefaultConfiguration()
    }

    /**
     * Loads asset defaults and applies them only when preferences still require initialization.
     *
     * @return Asset configuration, or embedded defaults after a handled loading failure.
     */
    fun loadDefaultConfiguration(): DefaultConfig {
        return try {
            appContext.assets.open("default_config.json").use { inputStream ->
                val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                val json = JSONObject(jsonString)
                val conf = json.getJSONObject("defaultConfig")

                val defaultConfig = DefaultConfig(
                    adBlockingEnabled = conf.optBoolean("adBlockingEnabled", true),
                    adultContentBlockingEnabled = conf.optBoolean("adultContentBlockingEnabled", true),
                    autoRestartOnBootEnabled = conf.optBoolean("autoRestartOnBootEnabled", true),
                    healthCheckInterval = conf.optLong("healthCheckInterval", 21_600_000L)
                )

                preferences.loadDefaultConfigIfNeeded(defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            val fallback = DefaultConfig()
            preferences.loadDefaultConfigIfNeeded(fallback)
            fallback
        }
    }

    /**
     * Returns whether Android VPN preparation requires no additional consent activity.
     */
    fun hasVpnPermission(): Boolean {
        return VpnService.prepare(appContext) == null
    }

    /**
     * Returns whether notification permission is granted, or true before Android 13.
     */
    fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Requests VPN startup after consent, enabling protection and advertising filtering.
     *
     * @param onPermissionRequired Callback invoked when the caller must obtain VPN consent.
     * Startup exceptions publish session failure; health scheduling failure is ignored.
     */
    fun startBlockingServices(onPermissionRequired: () -> Unit) {
        if (!hasVpnPermission()) {
            onPermissionRequired()
            return
        }

        try {
            loadDefaultConfiguration()
            preferences.setProtectionEnabled(true)
            preferences.setAdBlockingEnabled(true)
            UnblockerVpnService.start(appContext)
        } catch (_: Exception) {
            UnblockerVpnService.session.failed()
            return
        }
        // Diagnostics failure must not turn a healthy tunnel into a connection error.
        runCatching { scheduleHealthCheckWorker() }
    }

    /**
     * Requests VPN teardown and cancels periodic health work; service-command failure marks the session failed.
     */
    fun stopBlockingServices() {
        try {
            UnblockerVpnService.stop(appContext)
        } catch (_: Exception) {
            UnblockerVpnService.session.failed()
        } finally {
            cancelHealthCheckWorker()
        }
    }

    /**
     * Updates unique health work using the configured interval, clamped to the six-hour minimum.
     *
     * Cancels existing health work when stored protection intent is disabled.
     */
    fun scheduleHealthCheckWorker() {
        if (!HealthCheckPolicy.shouldRun(preferences.protectionEnabled.value)) {
            cancelHealthCheckWorker()
            return
        }
        val intervalMinutes = preferences.healthCheckIntervalMinutes.value.toLong()
            .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES.toLong())
        val workRequest = PeriodicWorkRequestBuilder<HealthCheckWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            HEALTH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun cancelHealthCheckWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(HEALTH_WORK_NAME)
    }

    /**
     * Process singleton for VPN startup coordination.
     */
    companion object {
        private const val HEALTH_WORK_NAME = "unblocker_health_check_work"
        @Volatile
        private var INSTANCE: QuickStartManager? = null

        /**
         * Returns the process singleton, initializing it with the application context if needed.
         *
         * @param context Context supplying the application context.
         * @return Shared quick-start coordinator.
         */
        fun getInstance(context: Context): QuickStartManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuickStartManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
