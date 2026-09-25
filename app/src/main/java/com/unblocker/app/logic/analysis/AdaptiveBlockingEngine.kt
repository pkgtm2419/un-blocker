package com.unblocker.app.logic.analysis

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision

enum class BlockingStrategy {
    STRICT_WHITELIST,            // Day 1: Only confirmed known ad networks (0.95f threshold, zero false positives)
    KNOWN_ADS_AND_PATTERNS,      // Day 2: Known ads + pattern match (0.90f)
    HEURISTIC_ANALYSIS,          // Day 3: Multi-factor heuristics + lexical tokens (0.85f)
    PROGRESSIVE_ADAPTIVE,        // Day 4-6: Dynamic thresholds with cadence/entropy (0.80f -> 0.76f)
    AGGRESSIVE_ADAPTIVE          // Day 7+: Full on-device autonomous blocking (0.75f, 75%+ ad reduction)
}

data class LearningPhase(
    val day: Int,
    val confidenceThreshold: Float,
    val blockingStrategy: BlockingStrategy,
    val description: String
)

/**
 * 7-Day Adaptive Learning Curve Optimization engine.
 * As detailed in Section 1.1.3 of un-blocker-improvement-plan.md.
 * Progressively tunes confidence thresholds over a 7-day on-device learning period:
 * - Day 1: Strict Whitelist (0.95 threshold) to ensure zero false positives on new installs.
 * - Days 2-6: Progressive learning integrating multi-factor signatures and behavioral cadence.
 * - Day 7+: Autonomous high-efficiency blocking reaching target 75%+ ad blocking.
 */
class AdaptiveBlockingEngine(
    private val preferences: FilteringPreferences? = null,
    val networkLearner: LocalNetworkLearner = LocalNetworkLearner()
) {
    val learningCurve = listOf(
        LearningPhase(1, 0.95f, BlockingStrategy.STRICT_WHITELIST, "Day 1: Strict Whitelist & Zero False-Positive Mode"),
        LearningPhase(2, 0.90f, BlockingStrategy.KNOWN_ADS_AND_PATTERNS, "Day 2: Known Networks & Core Patterns"),
        LearningPhase(3, 0.85f, BlockingStrategy.HEURISTIC_ANALYSIS, "Day 3: Heuristics & Lexical Token Analysis"),
        LearningPhase(4, 0.80f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 4: Progressive Multi-Factor Analysis"),
        LearningPhase(5, 0.78f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 5: Behavioral Cadence & Entropy Integration"),
        LearningPhase(6, 0.76f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 6: Advanced Tracker Recognition"),
        LearningPhase(7, 0.75f, BlockingStrategy.AGGRESSIVE_ADAPTIVE, "Day 7+: Autonomous On-Device Adaptive Shield")
    )

    fun getCurrentPhase(customDay: Int? = null): LearningPhase {
        val day = customDay ?: (preferences?.getDaysSinceInstall() ?: 1)
        val clampedIndex = (day - 1).coerceIn(0, learningCurve.size - 1)
        return learningCurve[clampedIndex]
    }

    fun evaluateDomain(domain: String, customDay: Int? = null): BlockingDecision {
        val phase = getCurrentPhase(customDay)
        val analysis = networkLearner.analyzeQuery(domain, threshold = phase.confidenceThreshold)

        return if (analysis.score >= phase.confidenceThreshold) {
            BlockingDecision(
                action = BlockingAction.BLOCK,
                reason = "${analysis.reason} (Phase: Day ${phase.day}, Confidence: ${analysis.score})",
                confidence = analysis.score,
                category = if (analysis.reason.contains("tracker", ignoreCase = true)) BlockingCategory.TRACKER else BlockingCategory.AD
            )
        } else {
            BlockingDecision.allow(
                reason = "Below Phase Day ${phase.day} threshold (${analysis.score} < ${phase.confidenceThreshold})",
                confidence = 1.0f - analysis.score
            )
        }
    }
}
