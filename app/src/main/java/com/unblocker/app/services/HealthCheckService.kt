package com.unblocker.app.services

import android.content.Context
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.ContentFilterEngine

data class HealthReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isVpnRunning: Boolean,
    val regressionChecksPassed: Int,
    val regressionChecksTotal: Int,
    val memoryUsageMb: Long,
    val statusMessage: String
)

class HealthCheckService(private val context: Context) {

    private val preferences = FilteringPreferences.getInstance(context)
    // Synthetic diagnostics must never train or persist production learning.
    private val filterEngine = ContentFilterEngine(context, preferences = preferences,
        networkLearner = com.unblocker.app.logic.analysis.LocalNetworkLearner(),
        allowlistedDomains = com.unblocker.app.logic.analysis.DeviceLearning.memoryDomainSet(),
        blocklistedDomains = com.unblocker.app.logic.analysis.DeviceLearning.memoryDomainSet())

    suspend fun performHealthCheck(): HealthReport {
        val isVpnRunning = UnblockerVpnService.isServiceActive.value

        // Fixed local smoke cases detect rule regressions; they do not measure real-world effectiveness.
        val testAdDomains = listOf(
            "googleads.g.doubleclick.net", "adservice.google.com", "pagead2.googlesyndication.com",
            "applovin.com", "unityads.unity3d.com", "vungle.com", "criteo.com", "taboola.com"
        )
        val testAdultDomains = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "chaturbate.com", "stripchat.com"
        )
        val testAllowedDomains = listOf(
            "google.com", "wikipedia.org", "github.com", "microsoft.com", "mozilla.org"
        )

        val cases = testAdDomains.map {
            BlockingRegressionCase(it, preferences.adBlockingEnabled.value)
        } + testAdultDomains.map {
            BlockingRegressionCase(it, preferences.adultBlockingEnabled.value)
        } + testAllowedDomains.map {
            BlockingRegressionCase(it, shouldBlock = false)
        }
        val result = BlockingRegressionCheck(cases).evaluate { domain ->
            filterEngine.analyzeAndFilter(domain).shouldBlock
        }
        preferences.setLastHealthCheckTime(System.currentTimeMillis())

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        val msg = if (result.passed == result.total) {
            "Local regression checks passed: ${result.passed}/${result.total}"
        } else {
            "Local regression check warning: ${result.passed}/${result.total} passed"
        }

        return HealthReport(
            isVpnRunning = isVpnRunning,
            regressionChecksPassed = result.passed,
            regressionChecksTotal = result.total,
            memoryUsageMb = usedMemoryMb,
            statusMessage = msg
        )
    }
}
