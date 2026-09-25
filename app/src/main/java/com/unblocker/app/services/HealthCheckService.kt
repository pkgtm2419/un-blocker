package com.unblocker.app.services

import android.content.Context
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.ContentFilterEngine

data class HealthReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isVpnRunning: Boolean,
    val isDatabaseHealthy: Boolean,
    val effectivenessScore: Float,
    val memoryUsageMb: Long,
    val totalConnectionsMonitored: Long,
    val statusMessage: String
)

class HealthCheckService(private val context: Context) {

    private val preferences = FilteringPreferences.getInstance(context)
    private val database = AppDatabase.getDatabase(context)
    private val filterEngine = ContentFilterEngine(context, preferences = preferences, database = database)

    suspend fun performHealthCheck(): HealthReport {
        // 1. Check VPN status
        val isVpnRunning = UnblockerVpnService.isServiceActive.value

        // 2. Database Integrity Check
        val isDbOk = database.checkIntegrity()

        // 3. Effectiveness Evaluation
        val testAdDomains = listOf(
            "googleads.g.doubleclick.net", "adservice.google.com", "pagead2.googlesyndication.com",
            "applovin.com", "unityads.unity3d.com", "vungle.com", "criteo.com", "taboola.com"
        )
        val testAdultDomains = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "chaturbate.com", "stripchat.com"
        )

        var passed = 0
        var total = 0

        for (domain in testAdDomains) {
            total++
            val res = filterEngine.analyzeAndFilter(domain)
            if (res.shouldBlock && res.contentType == com.unblocker.app.data.model.ContentType.AD) {
                passed++
            }
        }

        for (domain in testAdultDomains) {
            total++
            val res = filterEngine.analyzeAndFilter(domain)
            if (res.shouldBlock && res.contentType == com.unblocker.app.data.model.ContentType.ADULT_CONTENT) {
                passed++
            }
        }

        val effectiveness = if (total > 0) (passed.toFloat() / total.toFloat()) * 100f else 100f
        preferences.setHealthEffectivenessScore(effectiveness)
        preferences.setLastHealthCheckTime(System.currentTimeMillis())

        // 4. Memory Footprint
        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        // 5. Clean up old logs based on retention days
        val retentionDays = preferences.logRetentionDays.value
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        database.connectionDao().deleteOlderThan(cutoff)

        val totalConnections = database.connectionDao().countTotalConnections()

        val msg = if (isDbOk && effectiveness >= 90f) {
            "System Optimal: Protection active and effectiveness at ${"%.1f".format(effectiveness)}%"
        } else {
            "System Notice: Integrity OK: $isDbOk, Effectiveness: ${"%.1f".format(effectiveness)}%"
        }

        return HealthReport(
            isVpnRunning = isVpnRunning,
            isDatabaseHealthy = isDbOk,
            effectivenessScore = effectiveness,
            memoryUsageMb = usedMemoryMb,
            totalConnectionsMonitored = totalConnections,
            statusMessage = msg
        )
    }
}
