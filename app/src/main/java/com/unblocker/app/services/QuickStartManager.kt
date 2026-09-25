package com.unblocker.app.services

import android.content.Context
import android.net.VpnService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unblocker.app.data.model.AppConfigPayload
import com.unblocker.app.data.model.DefaultConfig
import com.unblocker.app.data.preferences.FilteringPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class ServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

class QuickStartManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val preferences = FilteringPreferences.getInstance(appContext)

    private val _status = MutableStateFlow(
        if (UnblockerVpnService.isServiceActive.value) ServiceStatus.RUNNING else ServiceStatus.STOPPED
    )
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    init {
        loadDefaultConfiguration()
        CoroutineScope(Dispatchers.Main).launch {
            UnblockerVpnService.isServiceActive.collect { active ->
                _status.value = if (active) ServiceStatus.RUNNING else ServiceStatus.STOPPED
                if (!active && preferences.serviceRunning.value) {
                    preferences.setServiceRunning(false)
                }
            }
        }
    }

    fun loadDefaultConfiguration(): DefaultConfig {
        return try {
            context.assets.open("default_config.json").use { inputStream ->
                val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                val json = JSONObject(jsonString)
                val conf = json.getJSONObject("defaultConfig")

                val defaultConfig = DefaultConfig(
                    adBlockingEnabled = conf.optBoolean("adBlockingEnabled", true),
                    adultContentBlockingEnabled = conf.optBoolean("adultContentBlockingEnabled", true),
                    parentalControlEnabled = conf.optBoolean("parentalControlEnabled", false),
                    backgroundMonitoringEnabled = conf.optBoolean("backgroundMonitoringEnabled", true),
                    connectionLoggingEnabled = conf.optBoolean("connectionLoggingEnabled", true),
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
        return VpnService.prepare(context) == null
    }

    /**
     * Start all blocker services with default settings.
     */
    fun startBlockingServices(onPermissionRequired: () -> Unit) {
        if (!hasVpnPermission()) {
            onPermissionRequired()
            return
        }

        _status.value = ServiceStatus.STARTING

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure default config is initialized
                loadDefaultConfiguration()

                // Launch VPN Service
                UnblockerVpnService.start(context)

                // Schedule Health Check Worker
                scheduleHealthCheckWorker()

                preferences.setServiceRunning(true)
                _status.value = ServiceStatus.RUNNING
            } catch (e: Exception) {
                _status.value = ServiceStatus.ERROR
            }
        }
    }

    /**
     * Stop all blocker services.
     */
    fun stopBlockingServices() {
        _status.value = ServiceStatus.STARTING
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UnblockerVpnService.stop(context)
                preferences.setServiceRunning(false)
                _status.value = ServiceStatus.STOPPED
            } catch (e: Exception) {
                _status.value = ServiceStatus.ERROR
            }
        }
    }

    fun scheduleHealthCheckWorker() {
        val intervalMinutes = preferences.healthCheckIntervalMinutes.value.toLong().coerceAtLeast(15)
        val workRequest = PeriodicWorkRequestBuilder<HealthCheckWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "unblocker_health_check_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    @android.annotation.SuppressLint("StaticFieldLeak")
    companion object {
        @Volatile
        private var INSTANCE: QuickStartManager? = null

        fun getInstance(context: Context): QuickStartManager {
            return INSTANCE ?: synchronized(this) {
                val instance = QuickStartManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
