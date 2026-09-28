package com.unblocker.app.logic.analysis

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision

enum class BlockingStrategy {
    STRICT_WHITELIST,
    KNOWN_ADS_AND_PATTERNS,
    HEURISTIC_ANALYSIS,
    PROGRESSIVE_ADAPTIVE,
    AGGRESSIVE_ADAPTIVE
}

data class LearningPhase(
    val day: Int,
    val confidenceThreshold: Float,
    val blockingStrategy: BlockingStrategy,
    val description: String
)

/**
 * Two-week local learning curve. Thresholds relax gradually while explicit safe domains,
 * static rules and user exceptions remain authoritative.
 */
class AdaptiveBlockingEngine(
    private val preferences: FilteringPreferences? = null,
    val networkLearner: LocalNetworkLearner = LocalNetworkLearner()
) {
    val learningCurve = listOf(
        LearningPhase(1, 0.90f, BlockingStrategy.STRICT_WHITELIST, "Day 1: Conservative local learning"),
        LearningPhase(2, 0.87f, BlockingStrategy.KNOWN_ADS_AND_PATTERNS, "Day 2: Known networks and core patterns"),
        LearningPhase(3, 0.84f, BlockingStrategy.HEURISTIC_ANALYSIS, "Day 3: Lexical and structural analysis"),
        LearningPhase(4, 0.82f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 4: Progressive multi-factor analysis"),
        LearningPhase(5, 0.80f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 5: Cadence and entropy signals"),
        LearningPhase(6, 0.78f, BlockingStrategy.PROGRESSIVE_ADAPTIVE, "Day 6: Adaptive tracker recognition"),
        LearningPhase(7, 0.77f, BlockingStrategy.AGGRESSIVE_ADAPTIVE, "Day 7: Stabilized local adaptation"),
        LearningPhase(14, 0.76f, BlockingStrategy.AGGRESSIVE_ADAPTIVE, "Day 14+: Mature local adaptation")
    )

    fun getCurrentPhase(customDay: Int? = null): LearningPhase {
        val day = customDay ?: (preferences?.getDaysSinceInstall() ?: 1)
        val normalizedDay = day.coerceAtLeast(1)
        return learningCurve.last { normalizedDay >= it.day }
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
