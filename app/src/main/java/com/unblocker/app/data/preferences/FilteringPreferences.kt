package com.unblocker.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.unblocker.app.data.model.DefaultConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class FilteringPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("unblocker_preferences", Context.MODE_PRIVATE)

    private val _adBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_AD_BLOCKING, true))
    val adBlockingEnabled: StateFlow<Boolean> = _adBlockingEnabled.asStateFlow()

    private val _adultBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADULT_BLOCKING, true))
    val adultBlockingEnabled: StateFlow<Boolean> = _adultBlockingEnabled.asStateFlow()

    private val _parentalControlEnabled = MutableStateFlow(prefs.getBoolean(KEY_PARENTAL_CONTROL, false))
    val parentalControlEnabled: StateFlow<Boolean> = _parentalControlEnabled.asStateFlow()

    private val _serviceRunning = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_RUNNING, false))
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _autoRestartOnBoot = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BOOT, true))
    val autoRestartOnBoot: StateFlow<Boolean> = _autoRestartOnBoot.asStateFlow()

    private val _healthCheckIntervalMinutes = MutableStateFlow(prefs.getInt(KEY_HEALTH_CHECK_INTERVAL, 5))
    val healthCheckIntervalMinutes: StateFlow<Int> = _healthCheckIntervalMinutes.asStateFlow()

    private val _logRetentionDays = MutableStateFlow(prefs.getInt(KEY_LOG_RETENTION, 30))
    val logRetentionDays: StateFlow<Int> = _logRetentionDays.asStateFlow()

    private val _lastHealthCheckTime = MutableStateFlow(prefs.getLong(KEY_LAST_HEALTH_CHECK, 0L))
    val lastHealthCheckTime: StateFlow<Long> = _lastHealthCheckTime.asStateFlow()

    private val _healthEffectivenessScore = MutableStateFlow(prefs.getFloat(KEY_EFFECTIVENESS_SCORE, 99.4f))
    val healthEffectivenessScore: StateFlow<Float> = _healthEffectivenessScore.asStateFlow()

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

    fun setAdBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_BLOCKING, enabled).apply()
        _adBlockingEnabled.value = enabled
    }

    fun setAdultBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT_BLOCKING, enabled).apply()
        _adultBlockingEnabled.value = enabled
    }

    fun setParentalControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PARENTAL_CONTROL, enabled).apply()
        _parentalControlEnabled.value = enabled
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
        prefs.edit().putInt(KEY_HEALTH_CHECK_INTERVAL, minutes).apply()
        _healthCheckIntervalMinutes.value = minutes
    }

    fun setLogRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_LOG_RETENTION, days).apply()
        _logRetentionDays.value = days
    }

    fun setLastHealthCheckTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_HEALTH_CHECK, time).apply()
        _lastHealthCheckTime.value = time
    }

    fun setHealthEffectivenessScore(score: Float) {
        prefs.edit().putFloat(KEY_EFFECTIVENESS_SCORE, score).apply()
        _healthEffectivenessScore.value = score
    }

    fun loadDefaultConfigIfNeeded(defaultConfig: DefaultConfig) {
        if (!prefs.contains(KEY_INITIALIZED)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_AD_BLOCKING, defaultConfig.adBlockingEnabled)
                .putBoolean(KEY_ADULT_BLOCKING, defaultConfig.adultContentBlockingEnabled)
                .putBoolean(KEY_PARENTAL_CONTROL, defaultConfig.parentalControlEnabled)
                .putBoolean(KEY_AUTO_BOOT, defaultConfig.autoRestartOnBootEnabled)
                .putInt(KEY_HEALTH_CHECK_INTERVAL, (defaultConfig.healthCheckInterval / 60000).toInt().coerceAtLeast(1))
                .putInt(KEY_LOG_RETENTION, defaultConfig.logRetentionDays)
                .apply()

            _adBlockingEnabled.value = defaultConfig.adBlockingEnabled
            _adultBlockingEnabled.value = defaultConfig.adultContentBlockingEnabled
            _parentalControlEnabled.value = defaultConfig.parentalControlEnabled
            _autoRestartOnBoot.value = defaultConfig.autoRestartOnBootEnabled
            _healthCheckIntervalMinutes.value = (defaultConfig.healthCheckInterval / 60000).toInt().coerceAtLeast(1)
            _logRetentionDays.value = defaultConfig.logRetentionDays
        }
    }

    @android.annotation.SuppressLint("StaticFieldLeak")
    companion object {
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_AD_BLOCKING = "ad_blocking_enabled"
        private const val KEY_ADULT_BLOCKING = "adult_blocking_enabled"
        private const val KEY_PARENTAL_CONTROL = "parental_control_enabled"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_AUTO_BOOT = "auto_restart_on_boot"
        private const val KEY_HEALTH_CHECK_INTERVAL = "health_check_interval"
        private const val KEY_LOG_RETENTION = "log_retention_days"
        private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
        private const val KEY_EFFECTIVENESS_SCORE = "effectiveness_score"
        private const val KEY_LEARNING_START_TIME = "learning_start_time"

        @Volatile
        private var INSTANCE: FilteringPreferences? = null

        fun getInstance(context: Context): FilteringPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = FilteringPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
