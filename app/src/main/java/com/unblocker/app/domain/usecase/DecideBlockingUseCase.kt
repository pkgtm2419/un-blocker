package com.unblocker.app.domain.usecase

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine

/**
 * Clean Architecture Use Case for evaluating network domains and deciding blocking actions.
 * Orchestrates static lists, 7-day adaptive multi-factor learning engine, and 18+ content filters.
 * Aligned with Section 2.3 of un-blocker-improvement-plan.md.
 */
class DecideBlockingUseCase(
    private val adDetector: AdDetector,
    private val adultContentDetector: AdultContentDetector,
    private val adaptiveBlockingEngine: AdaptiveBlockingEngine,
    private val isAdBlockingEnabled: () -> Boolean = { true },
    private val isAdultBlockingEnabled: () -> Boolean = { true }
) {
    constructor(
        adDetector: AdDetector,
        adultContentDetector: AdultContentDetector,
        adaptiveBlockingEngine: AdaptiveBlockingEngine,
        preferences: FilteringPreferences
    ) : this(
        adDetector = adDetector,
        adultContentDetector = adultContentDetector,
        adaptiveBlockingEngine = adaptiveBlockingEngine,
        isAdBlockingEnabled = { preferences.adBlockingEnabled.value },
        isAdultBlockingEnabled = { preferences.adultBlockingEnabled.value }
    )

    operator fun invoke(rawDomain: String): BlockingDecision {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) {
            return BlockingDecision.allow("Empty domain query", 1.0f)
        }

        val isAdBlocking = isAdBlockingEnabled()
        val isAdultBlocking = isAdultBlockingEnabled()

        // 1. Check Ad / Tracker Detection (Option 1)
        if (isAdBlocking) {
            // A. Static Seed List & Pre-default Patterns
            val (isAdSeed, seedReason) = adDetector.isAdDomain(domain)
            if (isAdSeed) {
                return BlockingDecision(
                    action = BlockingAction.BLOCK,
                    reason = seedReason,
                    confidence = 0.98f,
                    category = BlockingCategory.AD
                )
            }

            // B. 7-Day Adaptive Multi-Factor Analysis
            val adaptiveDecision = adaptiveBlockingEngine.evaluateDomain(domain)
            if (adaptiveDecision.isBlocked) {
                adDetector.addDomain(domain)
                return adaptiveDecision
            }
        }

        // 2. Check 18+ Adult Content Filtering (Option 2)
        if (isAdultBlocking) {
            val (isAdult, adultReason) = adultContentDetector.isAdultContent(domain)
            if (isAdult) {
                return BlockingDecision(
                    action = BlockingAction.BLOCK,
                    reason = adultReason,
                    confidence = 0.92f,
                    category = BlockingCategory.ADULT_CONTENT
                )
            }
        }

        // 3. Allowed Benign Traffic
        return BlockingDecision.allow("Allowed benign traffic", 1.0f)
    }
}
