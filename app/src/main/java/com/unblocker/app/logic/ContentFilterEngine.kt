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
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.analysis.PrivateDomainSet

/**
 * Autonomous local content filter engine.
 * Combines Clean Architecture UseCase orchestration with two-week adaptive multi-factor learning,
 * seed heuristics, and adult content classifier.
 * Operates 100% on-device with zero logs, zero history, and zero cloud dependency.
 */
class ContentFilterEngine(
    private val context: Context,
    private val adDetector: AdDetector = AdDetector(context),
    private val adultContentDetector: AdultContentDetector = AdultContentDetector(context),
    private val networkLearner: LocalNetworkLearner = LocalNetworkLearner(context),
    private val preferences: FilteringPreferences = FilteringPreferences.getInstance(context),
    private val allowlistedDomains: PrivateDomainSet = DeviceLearning.allowlist(context),
    val adaptiveEngine: AdaptiveBlockingEngine = AdaptiveBlockingEngine(preferences, networkLearner)
) {

    private val decideBlockingUseCase = DecideBlockingUseCase(
        adDetector = adDetector,
        adultContentDetector = adultContentDetector,
        adaptiveBlockingEngine = adaptiveEngine,
        isAllowlisted = allowlistedDomains::contains,
        isAdBlockingEnabled = { preferences.adBlockingEnabled.value },
        isAdultBlockingEnabled = { preferences.adultBlockingEnabled.value }
    )

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

        // Every request must reach behavioral learning and current settings.
        // Caching NORMAL decisions prevented cadence learning and toggle updates.
        return evaluateDomain(domain)
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

    fun getAdDetector(): AdDetector = adDetector
    fun getAdultContentDetector(): AdultContentDetector = adultContentDetector
    fun getNetworkLearner(): LocalNetworkLearner = networkLearner
    fun getAdaptiveBlockingEngine(): AdaptiveBlockingEngine = adaptiveEngine
    fun getDecideBlockingUseCase(): DecideBlockingUseCase = decideBlockingUseCase
}
