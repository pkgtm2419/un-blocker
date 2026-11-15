package com.unblocker.app.services

import android.content.Context
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.ContentFilterEngine
import com.unblocker.app.logic.rules.RuleSetHolder

/**
 * Local policy smoke-check report.
 *
 * @property timestamp Report creation time in Unix epoch milliseconds.
 * @property isVpnRunning Snapshot of established VPN activity.
 * @property regressionChecksPassed Number of synthetic cases matching expectations.
 * @property regressionChecksTotal Total synthetic cases evaluated.
 * @property statusMessage Human-readable pass or warning summary.
 */
data class HealthReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isVpnRunning: Boolean,
    val regressionChecksPassed: Int,
    val regressionChecksTotal: Int,
    val statusMessage: String
)

/**
 * Runs fixed local cases with volatile learning and user-rule sets.
 *
 * @param context Application context for assets and current filtering settings.
 */
class HealthCheckService(private val context: Context) {

    private val preferences = FilteringPreferences.getInstance(context)
    // Synthetic diagnostics must never train or persist production learning.
    private val filterEngine by lazy {
        ContentFilterEngine(
            context = context,
            adDetector = AdDetector(context, ruleSet = RuleSetHolder.get(context)),
            preferences = preferences,
            networkLearner = com.unblocker.app.logic.analysis.LocalNetworkLearner(),
            allowlistedDomains = com.unblocker.app.logic.analysis.DeviceLearning.memoryDomainSet(),
            blocklistedDomains = com.unblocker.app.logic.analysis.DeviceLearning.memoryDomainSet()
        )
    }

    /**
     * Evaluates fixed cases, records the health-check time and reports observed VPN activity.
     *
     * @return Local regression counts and status; no external effectiveness measurement is performed.
     */
    suspend fun performHealthCheck(): HealthReport {
        val isVpnRunning = UnblockerVpnService.isServiceActive.value

        // Fixed local smoke cases detect rule regressions; they do not measure real-world effectiveness.
        val testAdDomains = listOf(
            "googleads.g.doubleclick.net", "adservice.google.com", "pagead2.googlesyndication.com",
            "a.applovin.com", "unityads.unity3d.com", "vungle.com", "criteo.com", "taboola.com"
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

        val msg = if (result.passed == result.total) {
            "Local regression checks passed: ${result.passed}/${result.total}"
        } else {
            "Local regression check warning: ${result.passed}/${result.total} passed"
        }

        return HealthReport(
            isVpnRunning = isVpnRunning,
            regressionChecksPassed = result.passed,
            regressionChecksTotal = result.total,
            statusMessage = msg
        )
    }
}
