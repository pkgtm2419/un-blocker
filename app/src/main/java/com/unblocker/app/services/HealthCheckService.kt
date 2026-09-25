package com.unblocker.app.services

import android.content.Context
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.ContentFilterEngine

data class HealthReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isVpnRunning: Boolean,
    val effectivenessScore: Float,
    val memoryUsageMb: Long,
    val statusMessage: String
)

class HealthCheckService(private val context: Context) {

    private val preferences = FilteringPreferences.getInstance(context)
    private val filterEngine = ContentFilterEngine(context, preferences = preferences)

    suspend fun performHealthCheck(): HealthReport {
        val isVpnRunning = UnblockerVpnService.isServiceActive.value

        // Effectiveness Evaluation across sample ad & adult domains
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

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        val msg = if (effectiveness >= 90f) {
            "System Optimal: Autonomous local analysis active at ${"%.1f".format(effectiveness)}%"
        } else {
            "System Notice: Effectiveness: ${"%.1f".format(effectiveness)}%"
        }

        return HealthReport(
            isVpnRunning = isVpnRunning,
            effectivenessScore = effectiveness,
            memoryUsageMb = usedMemoryMb,
            statusMessage = msg
        )
    }
}
