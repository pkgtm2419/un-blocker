package com.unblocker.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.unblocker.app.data.model.DefaultConfig
import com.unblocker.app.services.HealthCheckPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class FilteringPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("unblocker_preferences", Context.MODE_PRIVATE)

    private val _adBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_AD_BLOCKING, true))
    val adBlockingEnabled: StateFlow<Boolean> = _adBlockingEnabled.asStateFlow()

    private val _adultBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADULT_BLOCKING, true))
    val adultBlockingEnabled: StateFlow<Boolean> = _adultBlockingEnabled.asStateFlow()

    private val _serviceRunning = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_RUNNING, false))
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    // User intent is separate from the ephemeral service/process state.
    private val _protectionEnabled = MutableStateFlow(prefs.getBoolean(KEY_PROTECTION_ENABLED,
        prefs.getBoolean(KEY_SERVICE_RUNNING, false) && prefs.getBoolean(KEY_AD_BLOCKING, true)).also {
        if (!prefs.contains(KEY_PROTECTION_ENABLED)) {
            prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, it).commit()
        }
    })
    val protectionEnabled: StateFlow<Boolean> = _protectionEnabled.asStateFlow()

    fun setProtectionEnabled(enabled: Boolean) {
        val committed = runCatching { prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).commit() }.getOrDefault(false)
        if (!committed) {
            prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
        }
        _protectionEnabled.value = enabled
    }

    private val _autoRestartOnBoot = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BOOT, true))
    val autoRestartOnBoot: StateFlow<Boolean> = _autoRestartOnBoot.asStateFlow()

    private val _healthCheckIntervalMinutes = MutableStateFlow(
        prefs.getInt(KEY_HEALTH_CHECK_INTERVAL, HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
            .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES).also { interval ->
                if (prefs.getInt(KEY_HEALTH_CHECK_INTERVAL, interval) != interval) {
                    prefs.edit().putInt(KEY_HEALTH_CHECK_INTERVAL, interval).apply()
                }
            }
    )
    val healthCheckIntervalMinutes: StateFlow<Int> = _healthCheckIntervalMinutes.asStateFlow()

    private val _lastHealthCheckTime = MutableStateFlow(prefs.getLong(KEY_LAST_HEALTH_CHECK, 0L))
    val lastHealthCheckTime: StateFlow<Long> = _lastHealthCheckTime.asStateFlow()

    private val _learningStartTime = MutableStateFlow(
        prefs.getLong(KEY_LEARNING_START_TIME, 0L).let {
            if (it == 0L) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LEARNING_START_TIME, now).apply()
                now
            } else {
                it
            }
        }
    )
    val learningStartTime: StateFlow<Long> = _learningStartTime.asStateFlow()

    fun getDaysSinceInstall(): Int {
        val start = _learningStartTime.value
        val now = System.currentTimeMillis()
        val days = ((now - start) / (86_400_000L)).toInt() + 1
        return days.coerceAtLeast(1)
    }

    fun resetLearningStartDateForTesting(startTime: Long) {
        prefs.edit().putLong(KEY_LEARNING_START_TIME, startTime).apply()
        _learningStartTime.value = startTime
    }

    fun resetLearning() {
        val now = System.currentTimeMillis()
        check(prefs.edit().putLong(KEY_LEARNING_START_TIME, now).commit())
        _learningStartTime.value = now
    }

    fun setAdBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_BLOCKING, enabled).apply()
        _adBlockingEnabled.value = enabled
    }

    fun setAdultBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT_BLOCKING, enabled).apply()
        _adultBlockingEnabled.value = enabled
    }

    fun setServiceRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
        _serviceRunning.value = running
    }

    fun setAutoRestartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
        _autoRestartOnBoot.value = enabled
    }

    fun setHealthCheckIntervalMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
        prefs.edit().putInt(KEY_HEALTH_CHECK_INTERVAL, safeMinutes).apply()
        _healthCheckIntervalMinutes.value = safeMinutes
    }

    fun setLastHealthCheckTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_HEALTH_CHECK, time).apply()
        _lastHealthCheckTime.value = time
    }

    fun loadDefaultConfigIfNeeded(defaultConfig: DefaultConfig) {
        if (!prefs.contains(KEY_INITIALIZED)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_AD_BLOCKING, defaultConfig.adBlockingEnabled)
                .putBoolean(KEY_ADULT_BLOCKING, defaultConfig.adultContentBlockingEnabled)
                .putBoolean(KEY_AUTO_BOOT, defaultConfig.autoRestartOnBootEnabled)
                .putInt(KEY_HEALTH_CHECK_INTERVAL, (defaultConfig.healthCheckInterval / 60000).toInt()
                    .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES))
                .apply()

            _adBlockingEnabled.value = defaultConfig.adBlockingEnabled
            _adultBlockingEnabled.value = defaultConfig.adultContentBlockingEnabled
            _autoRestartOnBoot.value = defaultConfig.autoRestartOnBootEnabled
            _healthCheckIntervalMinutes.value = (defaultConfig.healthCheckInterval / 60000).toInt()
                .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
        }
    }

    companion object {
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_AD_BLOCKING = "ad_blocking_enabled"
        private const val KEY_ADULT_BLOCKING = "adult_blocking_enabled"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_AUTO_BOOT = "auto_restart_on_boot"
        private const val KEY_HEALTH_CHECK_INTERVAL = "health_check_interval"
        private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
        private const val KEY_LEARNING_START_TIME = "learning_start_time"

        @Volatile
        private var INSTANCE: FilteringPreferences? = null

        fun getInstance(context: Context): FilteringPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FilteringPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
