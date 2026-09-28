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

class QuickStartManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = FilteringPreferences.getInstance(appContext)

    val status: StateFlow<ServiceStatus> = UnblockerVpnService.session.status

    init {
        loadDefaultConfiguration()
    }

    fun loadDefaultConfiguration(): DefaultConfig {
        return try {
            appContext.assets.open("default_config.json").use { inputStream ->
                val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                val json = JSONObject(jsonString)
                val conf = json.getJSONObject("defaultConfig")

                val defaultConfig = DefaultConfig(
                    adBlockingEnabled = conf.optBoolean("adBlockingEnabled", true),
                    adultContentBlockingEnabled = conf.optBoolean("adultContentBlockingEnabled", true),
                    parentalControlEnabled = conf.optBoolean("parentalControlEnabled", false),
                    backgroundMonitoringEnabled = conf.optBoolean("backgroundMonitoringEnabled", true),
                    connectionLoggingEnabled = false,
                    selfCheckingEnabled = conf.optBoolean("selfCheckingEnabled", true),
                    autoRestartOnBootEnabled = conf.optBoolean("autoRestartOnBootEnabled", true),
                    healthCheckInterval = conf.optLong("healthCheckInterval", 300_000L),
                    logRetentionDays = conf.optInt("logRetentionDays", 30)
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
     * Checks if Android VPN permission has been prepared/granted.
     * Returns true if granted (prepare returns null).
     */
    fun hasVpnPermission(): Boolean {
        return VpnService.prepare(appContext) == null
    }

    /**
     * Start all blocker services with default settings.
     */
    fun startBlockingServices(onPermissionRequired: () -> Unit) {
        if (!hasVpnPermission()) {
            onPermissionRequired()
            return
        }

        try {
            loadDefaultConfiguration()
            preferences.setProtectionEnabled(true)
            UnblockerVpnService.start(appContext)
        } catch (_: Exception) {
            UnblockerVpnService.session.failed()
            return
        }
        // Diagnostics failure must not turn a healthy tunnel into a connection error.
        runCatching { scheduleHealthCheckWorker() }
    }

    /**
     * Stop all blocker services.
     */
    fun stopBlockingServices() {
        try {
            UnblockerVpnService.stop(appContext)
        } catch (_: Exception) {
            UnblockerVpnService.session.failed()
        }
    }

    fun scheduleHealthCheckWorker() {
        val intervalMinutes = preferences.healthCheckIntervalMinutes.value.toLong().coerceAtLeast(15)
        val workRequest = PeriodicWorkRequestBuilder<HealthCheckWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "unblocker_health_check_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: QuickStartManager? = null

        fun getInstance(context: Context): QuickStartManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuickStartManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
