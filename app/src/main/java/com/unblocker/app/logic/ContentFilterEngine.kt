package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.data.model.ContentType
import com.unblocker.app.data.model.DetectionMethod
import com.unblocker.app.data.model.FilterResult
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import java.util.concurrent.ConcurrentHashMap

/**
 * Autonomous local content filter engine.
 * Combines Clean Architecture UseCase orchestration with 7-day adaptive multi-factor learning,
 * seed heuristics, and adult content classifier.
 * Operates 100% on-device with zero logs, zero history, and zero cloud dependency.
 */
class ContentFilterEngine(
    private val context: Context,
    private val adDetector: AdDetector = AdDetector(context),
    private val adultContentDetector: AdultContentDetector = AdultContentDetector(context),
    private val networkLearner: LocalNetworkLearner = LocalNetworkLearner(context),
    private val preferences: FilteringPreferences = FilteringPreferences.getInstance(context),
    val adaptiveEngine: AdaptiveBlockingEngine = AdaptiveBlockingEngine(preferences, networkLearner)
) {

    private val decideBlockingUseCase = DecideBlockingUseCase(
        adDetector = adDetector,
        adultContentDetector = adultContentDetector,
        adaptiveBlockingEngine = adaptiveEngine,
        preferences = preferences
    )

    // Ephemeral in-memory evaluation cache for instant sub-millisecond response
    private val decisionCache = ConcurrentHashMap<String, FilterResult>()
    private val maxCacheSize = 3000

    fun analyzeAndFilter(rawDomain: String): FilterResult {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) {
            return FilterResult(
                domain = domain,
                contentType = ContentType.NORMAL,
                shouldBlock = false,
                reason = "Empty domain",
                detectionMethod = DetectionMethod.NONE,
                confidence = 1.0f
            )
        }

        val isAdBlocking = preferences.adBlockingEnabled.value
        val isAdultBlocking = preferences.adultBlockingEnabled.value

        // Check in-memory decision cache
        val cached = decisionCache[domain]
        if (cached != null) {
            // Verify cached result matches current toggle states
            val matchesAdToggle = cached.contentType != ContentType.AD || cached.shouldBlock == isAdBlocking
            val matchesAdultToggle = cached.contentType != ContentType.ADULT_CONTENT || cached.shouldBlock == isAdultBlocking
            if (matchesAdToggle && matchesAdultToggle) {
                return cached
            }
        }

        val result = evaluateDomain(domain)

        if (decisionCache.size < maxCacheSize) {
            decisionCache[domain] = result
        }

        return result
    }

    private fun evaluateDomain(domain: String): FilterResult {
        val decision = decideBlockingUseCase(domain)

        val contentType = when (decision.category) {
            BlockingCategory.AD, BlockingCategory.TRACKER -> ContentType.AD
            BlockingCategory.ADULT_CONTENT -> ContentType.ADULT_CONTENT
            BlockingCategory.NORMAL -> ContentType.NORMAL
        }

        val method = if (!decision.isBlocked) {
            DetectionMethod.NONE
        } else if (decision.category == BlockingCategory.ADULT_CONTENT) {
            if (decision.reason.contains("TLD")) DetectionMethod.TLD_RULE
            else if (decision.reason.contains("Pattern")) DetectionMethod.PATTERN_MATCH
            else DetectionMethod.STATIC_LIST
        } else {
            if (decision.reason.contains("Static") || decision.reason.contains("Suffix")) DetectionMethod.STATIC_LIST
            else DetectionMethod.PATTERN_MATCH
        }

        return FilterResult(
            domain = domain,
            contentType = contentType,
            shouldBlock = decision.isBlocked,
            reason = decision.reason,
            detectionMethod = method,
            confidence = decision.confidence
        )
    }

    fun clearCache() {
        decisionCache.clear()
    }

    fun getAdDetector(): AdDetector = adDetector
    fun getAdultContentDetector(): AdultContentDetector = adultContentDetector
    fun getNetworkLearner(): LocalNetworkLearner = networkLearner
    fun getAdaptiveBlockingEngine(): AdaptiveBlockingEngine = adaptiveEngine
    fun getDecideBlockingUseCase(): DecideBlockingUseCase = decideBlockingUseCase
}
