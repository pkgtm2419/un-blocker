package com.unblocker.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.unblocker.app.data.model.DefaultConfig
import com.unblocker.app.services.HealthCheckPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted policy mode. APPROVED_ONLY applies user overrides and allows otherwise unmatched hosts.
 */
enum class BlockingMode {
    SMART,
    APPROVED_ONLY,
}

/**
 * Persisted filtering tier consumed by the filtering policy; BASIC includes compatibility exceptions.
 */
enum class FilteringTier {
    BASIC,
    OPTIMAL,
    COMPLETE,
}

/**
 * Minimal preference boundary for controlling local query logging.
 */
interface FilteringPreferenceStore {
    /**
     * Whether capture of new local query log entries is paused.
     */
    val isQueryLoggingPaused: StateFlow<Boolean>

    /**
     * Persists the local query logging pause flag.
     *
     * @param paused Whether new log capture should be paused.
     */
    fun setQueryLoggingPaused(paused: Boolean)
}

/**
 * Stores filtering preferences and exposes local reactive snapshots of persisted settings.
 *
 * @param context Context used to access the application-private preference file.
 */
class FilteringPreferences(
    context: Context,
) : FilteringPreferenceStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("unblocker_preferences", Context.MODE_PRIVATE)

    private val _adBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_AD_BLOCKING, true))

    /**
     * Whether ad and tracking rule evaluation is enabled.
     */
    val adBlockingEnabled: StateFlow<Boolean> = _adBlockingEnabled.asStateFlow()

    private val _adultBlockingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADULT_BLOCKING, true))

    /**
     * Whether the independent adult-domain filtering policy is enabled.
     */
    val adultBlockingEnabled: StateFlow<Boolean> = _adultBlockingEnabled.asStateFlow()

    private val _serviceRunning = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_RUNNING, false))

    /**
     * Last persisted service-running flag; it is not a live tunnel health signal.
     */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    // User intent is separate from the ephemeral service/process state.
    private val _protectionEnabled =
        MutableStateFlow(
            prefs
                .getBoolean(
                    KEY_PROTECTION_ENABLED,
                    prefs.getBoolean(KEY_SERVICE_RUNNING, false) && prefs.getBoolean(KEY_AD_BLOCKING, true),
                ).also {
                    if (!prefs.contains(KEY_PROTECTION_ENABLED)) {
                        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, it).apply()
                    }
                },
        )

    /**
     * Persisted user intent to enable protection, separate from current service state.
     */
    val protectionEnabled: StateFlow<Boolean> = _protectionEnabled.asStateFlow()

    /**
     * Persists protection intent without starting or stopping the service.
     *
     * @param enabled Whether the user requests protection.
     */
    fun setProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
        _protectionEnabled.value = enabled
    }

    private val _autoRestartOnBoot = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BOOT, true))

    /**
     * Whether protection restart is requested after device boot.
     */
    val autoRestartOnBoot: StateFlow<Boolean> = _autoRestartOnBoot.asStateFlow()

    private val _healthCheckIntervalMinutes =
        MutableStateFlow(
            prefs
                .getInt(KEY_HEALTH_CHECK_INTERVAL, HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
                .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
                .also { interval ->
                    if (prefs.getInt(KEY_HEALTH_CHECK_INTERVAL, interval) != interval) {
                        prefs.edit().putInt(KEY_HEALTH_CHECK_INTERVAL, interval).apply()
                    }
                },
        )

    /**
     * Persisted health-check interval in minutes, clamped to the policy minimum.
     */
    val healthCheckIntervalMinutes: StateFlow<Int> = _healthCheckIntervalMinutes.asStateFlow()

    private val _lastHealthCheckTime = MutableStateFlow(prefs.getLong(KEY_LAST_HEALTH_CHECK, 0L))

    /**
     * Last health-check timestamp in Unix epoch milliseconds; zero means unset.
     */
    val lastHealthCheckTime: StateFlow<Long> = _lastHealthCheckTime.asStateFlow()

    private val _learningStartTime =
        MutableStateFlow(
            prefs.getLong(KEY_LEARNING_START_TIME, 0L).let {
                if (it == 0L) {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LEARNING_START_TIME, now).apply()
                    now
                } else {
                    it
                }
            },
        )

    /**
     * Start timestamp of the current learning period in Unix epoch milliseconds.
     */
    val learningStartTime: StateFlow<Long> = _learningStartTime.asStateFlow()

    private val _blockingMode =
        MutableStateFlow(
            try {
                BlockingMode.valueOf(
                    prefs.getString(KEY_BLOCKING_MODE, BlockingMode.SMART.name) ?: BlockingMode.SMART.name,
                )
            } catch (_: Exception) {
                BlockingMode.SMART
            },
        )

    /**
     * Current persisted domain policy mode.
     */
    val blockingMode: StateFlow<BlockingMode> = _blockingMode.asStateFlow()

    /**
     * Persists the domain policy mode.
     *
     * @param mode Policy mode to select.
     */
    fun setBlockingMode(mode: BlockingMode) {
        prefs.edit().putString(KEY_BLOCKING_MODE, mode.name).apply()
        _blockingMode.value = mode
    }

    private val _filteringTier =
        MutableStateFlow(
            try {
                FilteringTier.valueOf(
                    prefs.getString(KEY_FILTERING_TIER, FilteringTier.OPTIMAL.name) ?: FilteringTier.OPTIMAL.name,
                )
            } catch (_: Exception) {
                FilteringTier.OPTIMAL
            },
        )

    /**
     * Current persisted filtering tier.
     */
    val filteringTier: StateFlow<FilteringTier> = _filteringTier.asStateFlow()

    /**
     * Persists the filtering tier.
     *
     * @param tier Policy tier to select.
     */
    fun setFilteringTier(tier: FilteringTier) {
        prefs.edit().putString(KEY_FILTERING_TIER, tier.name).apply()
        _filteringTier.value = tier
    }

    private val _pausedUntil = MutableStateFlow(prefs.getLong(KEY_PAUSED_UNTIL, 0L))

    /**
     * Protection pause expiry in Unix epoch milliseconds; zero means no requested pause.
     */
    val pausedUntil: StateFlow<Long> = _pausedUntil.asStateFlow()

    /**
     * Sets the pause expiry relative to the current wall clock.
     *
     * @param durationMs Requested pause duration in milliseconds.
     */
    fun pauseProtection(durationMs: Long = 5 * 60 * 1000L) {
        val until = System.currentTimeMillis() + durationMs
        prefs.edit().putLong(KEY_PAUSED_UNTIL, until).apply()
        _pausedUntil.value = until
    }

    /**
     * Clears the persisted protection pause expiry.
     */
    fun resumeProtection() {
        prefs.edit().putLong(KEY_PAUSED_UNTIL, 0L).apply()
        _pausedUntil.value = 0L
    }

    /**
     * Checks whether the current wall clock precedes the pause expiry.
     *
     * @return True while the persisted pause remains active.
     */
    fun isPaused(): Boolean = System.currentTimeMillis() < _pausedUntil.value

    private val _blockAnalyticsBreakage = MutableStateFlow(prefs.getBoolean(KEY_BLOCK_ANALYTICS_BREAKAGE, true))

    /**
     * Persisted preference controlling analytics blocking despite possible app breakage.
     */
    val blockAnalyticsBreakage: StateFlow<Boolean> = _blockAnalyticsBreakage.asStateFlow()

    /**
     * Persists the analytics-blocking preference.
     *
     * @param enabled Whether analytics blocking despite breakage is requested.
     */
    fun setBlockAnalyticsBreakage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_ANALYTICS_BREAKAGE, enabled).apply()
        _blockAnalyticsBreakage.value = enabled
    }

    /**
     * Reports the one-based age of the current learning period.
     *
     * @return Elapsed whole 24-hour periods plus one, with a minimum of one.
     */
    fun getDaysSinceInstall(): Int {
        val start = _learningStartTime.value
        val now = System.currentTimeMillis()
        val days = ((now - start) / (86_400_000L)).toInt() + 1
        return days.coerceAtLeast(1)
    }

    /**
     * Overrides the persisted learning-period origin for tests.
     *
     * @param startTime Replacement Unix epoch timestamp in milliseconds.
     */
    fun resetLearningStartDateForTesting(startTime: Long) {
        prefs.edit().putLong(KEY_LEARNING_START_TIME, startTime).apply()
        _learningStartTime.value = startTime
    }

    /**
     * Resets the learning-period timestamp after synchronously persisting it.
     *
     * @throws IllegalStateException If the preference commit fails.
     */
    fun resetLearning() {
        val now = System.currentTimeMillis()
        check(prefs.edit().putLong(KEY_LEARNING_START_TIME, now).commit())
        _learningStartTime.value = now
    }

    /**
     * Persists the ad and tracking filtering preference.
     *
     * @param enabled Whether ad and tracking rule evaluation is enabled.
     */
    fun setAdBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_BLOCKING, enabled).apply()
        _adBlockingEnabled.value = enabled
    }

    /**
     * Persists the independent adult-domain filtering preference.
     *
     * @param enabled Whether adult-domain evaluation is enabled.
     */
    fun setAdultBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT_BLOCKING, enabled).apply()
        _adultBlockingEnabled.value = enabled
    }

    /**
     * Persists the last reported service state.
     *
     * @param running Whether the service reports itself running.
     */
    fun setServiceRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
        _serviceRunning.value = running
    }

    /**
     * Persists protection restart intent for boot handling.
     *
     * @param enabled Whether boot handling should request protection restart.
     */
    fun setAutoRestartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BOOT, enabled).apply()
        _autoRestartOnBoot.value = enabled
    }

    /**
     * Persists the interval after clamping it to the health policy minimum.
     *
     * @param minutes Requested health-check interval in minutes.
     */
    fun setHealthCheckIntervalMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
        prefs.edit().putInt(KEY_HEALTH_CHECK_INTERVAL, safeMinutes).apply()
        _healthCheckIntervalMinutes.value = safeMinutes
    }

    /**
     * Persists the latest health-check time.
     *
     * @param time Unix epoch timestamp in milliseconds.
     */
    fun setLastHealthCheckTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_HEALTH_CHECK, time).apply()
        _lastHealthCheckTime.value = time
    }

    /**
     * Applies bundled defaults only when the initialization preference is absent.
     *
     * @param defaultConfig Defaults whose health-check interval is expressed in milliseconds.
     */
    fun loadDefaultConfigIfNeeded(defaultConfig: DefaultConfig) {
        if (!prefs.contains(KEY_INITIALIZED)) {
            prefs
                .edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_AD_BLOCKING, defaultConfig.adBlockingEnabled)
                .putBoolean(KEY_ADULT_BLOCKING, defaultConfig.adultContentBlockingEnabled)
                .putBoolean(KEY_AUTO_BOOT, defaultConfig.autoRestartOnBootEnabled)
                .putInt(
                    KEY_HEALTH_CHECK_INTERVAL,
                    (defaultConfig.healthCheckInterval / 60000)
                        .toInt()
                        .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES),
                ).putString(KEY_BLOCKING_MODE, BlockingMode.SMART.name)
                .apply()

            _adBlockingEnabled.value = defaultConfig.adBlockingEnabled
            _adultBlockingEnabled.value = defaultConfig.adultContentBlockingEnabled
            _autoRestartOnBoot.value = defaultConfig.autoRestartOnBootEnabled
            _healthCheckIntervalMinutes.value =
                (defaultConfig.healthCheckInterval / 60000)
                    .toInt()
                    .coerceAtLeast(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
            _blockingMode.value = BlockingMode.SMART
        }
    }

    private val _isQueryLoggingPaused = MutableStateFlow(prefs.getBoolean(KEY_QUERY_LOGGING_PAUSED, false))

    /**
     * Persisted pause flag for capture of new local query log entries.
     */
    override val isQueryLoggingPaused: StateFlow<Boolean> = _isQueryLoggingPaused.asStateFlow()

    /**
     * Persists and publishes the query logging pause flag.
     *
     * @param paused Whether new log capture should be paused.
     */
    override fun setQueryLoggingPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_QUERY_LOGGING_PAUSED, paused).apply()
        _isQueryLoggingPaused.value = paused
    }

    /**
     * Persisted preference keys and the application-scoped store accessor.
     */
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
        private const val KEY_QUERY_LOGGING_PAUSED = "query_logging_paused"
        private const val KEY_BLOCKING_MODE = "blocking_mode"
        private const val KEY_FILTERING_TIER = "filtering_tier"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_BLOCK_ANALYTICS_BREAKAGE = "block_analytics_breakage"

        @Volatile
        private var INSTANCE: FilteringPreferences? = null

        /**
         * Returns the process-wide preference store.
         *
         * @param context Context whose application context is retained.
         * @return Shared filtering preferences.
         */
        fun getInstance(context: Context): FilteringPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FilteringPreferences(context.applicationContext).also { INSTANCE = it }
            }
    }
}
