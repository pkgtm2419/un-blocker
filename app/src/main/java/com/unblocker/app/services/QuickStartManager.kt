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
        } finally {
            cancelHealthCheckWorker()
        }
    }

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

    companion object {
        private const val HEALTH_WORK_NAME = "unblocker_health_check_work"
        @Volatile
        private var INSTANCE: QuickStartManager? = null

        fun getInstance(context: Context): QuickStartManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuickStartManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
