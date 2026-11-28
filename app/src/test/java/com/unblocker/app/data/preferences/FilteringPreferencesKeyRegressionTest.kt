package com.unblocker.app.data.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.unblocker.app.services.HealthCheckPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test guaranteeing all persisted SharedPreferences keys in [FilteringPreferences]
 * retain exact backward-compatible names and value round-trips across Settings UI refactors (WP-13).
 */
class FilteringPreferencesKeyRegressionTest {
    @Test
    fun settersPersistUnderExactLegacyPreferenceKeys() {
        val backingPrefs = InMemorySharedPreferences()
        val fakeContext =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this

                override fun getSharedPreferences(
                    name: String?,
                    mode: Int,
                ): SharedPreferences {
                    assertEquals("unblocker_preferences", name)
                    return backingPrefs
                }
            }

        val store = FilteringPreferences(fakeContext)
        store.setAdBlockingEnabled(false)
        store.setAdultBlockingEnabled(false)
        store.setServiceRunning(true)
        store.setProtectionEnabled(true)
        store.setAutoRestartOnBoot(false)
        val requestedInterval = HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES + 15
        store.setHealthCheckIntervalMinutes(requestedInterval)
        store.setLastHealthCheckTime(123456L)
        store.setQueryLoggingPaused(true)
        store.setBlockingMode(BlockingMode.APPROVED_ONLY)
        store.setFilteringTier(FilteringTier.COMPLETE)
        store.setBlockAnalyticsBreakage(false)
        store.setIsolatedSession(active = true, targetPackage = "com.example.app")
        store.setExcludedPackages(setOf("com.example.bank"))
        store.pauseProtection(durationMs = 60_000L)

        val data = backingPrefs.snapshot()
        assertFalse(data["ad_blocking_enabled"] as Boolean)
        assertFalse(data["adult_blocking_enabled"] as Boolean)
        assertTrue(data["service_running"] as Boolean)
        assertTrue(data["protection_enabled"] as Boolean)
        assertFalse(data["auto_restart_on_boot"] as Boolean)
        assertEquals(requestedInterval, data["health_check_interval"])
        assertEquals(123456L, data["last_health_check"])
        assertTrue(data["learning_start_time"] is Long)
        assertTrue(data["query_logging_paused"] as Boolean)
        assertEquals("APPROVED_ONLY", data["blocking_mode"])
        assertEquals("COMPLETE", data["filtering_tier"])
        assertFalse(data["block_analytics_breakage"] as Boolean)
        assertTrue(data["isolated_session_active"] as Boolean)
        assertEquals("com.example.app", data["isolated_session_package"])
        assertEquals(setOf("com.example.bank"), data["excluded_packages"])
        assertTrue((data["paused_until"] as Long) > 0L)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

        fun snapshot(): Map<String, Any?> = map.toMap()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = (map[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = (map[key] as? Int) ?: defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = (map[key] as? Long) ?: defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = (map[key] as? Float) ?: defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = (map[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor =
            object : SharedPreferences.Editor {
                private val pending = linkedMapOf<String, Any?>()
                private var clearAll = false

                override fun putString(
                    key: String?,
                    value: String?,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }

                override fun putStringSet(
                    key: String?,
                    values: MutableSet<String>?,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = values }

                override fun putInt(
                    key: String?,
                    value: Int,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }

                override fun putLong(
                    key: String?,
                    value: Long,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }

                override fun putFloat(
                    key: String?,
                    value: Float,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }

                override fun putBoolean(
                    key: String?,
                    value: Boolean,
                ): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }

                override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) pending[key] = null }

                override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

                override fun commit(): Boolean {
                    apply()
                    return true
                }

                override fun apply() {
                    if (clearAll) map.clear()
                    pending.forEach { (k, v) ->
                        if (v == null) map.remove(k) else map[k] = v
                    }
                }
            }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
