package com.unblocker.app.logic.analysis

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.*

/**
 * Compatibility strategy identifier for evidence-based decisions.
 */
enum class BlockingStrategy { EVIDENCE_BASED, }

/**
 * Compatibility description of the evidence-based learning phase.
 *
 * @property day Nominal phase day; installation age does not authorize blocking.
 * @property confidenceThreshold Descriptive confidence threshold on a zero-to-one scale.
 * @property blockingStrategy Strategy identifier.
 * @property description Human-readable phase description.
 */
data class LearningPhase(
    val day: Int,
    val confidenceThreshold: Float,
    val blockingStrategy: BlockingStrategy,
    val description: String,
)

/**
 * Compatibility facade whose blocking authorization depends on evidence rather than installation age.
 *
 * @param preferences Retained compatibility parameter; unused.
 * @property networkLearner Local observation and reputation evaluator.
 */
class AdaptiveBlockingEngine(
    @Suppress("UNUSED_PARAMETER") preferences: FilteringPreferences? = null,
    val networkLearner: LocalNetworkLearner = LocalNetworkLearner(),
) {
    /**
     * Single compatibility phase describing independent local evidence.
     */
    val learningCurve = listOf(LearningPhase(1, .8f, BlockingStrategy.EVIDENCE_BASED, "Independent local evidence"))
    /**
     * Returns the single evidence-based phase.
     *
     * @param customDay Compatibility parameter; ignored.
     * @return The only phase in learningCurve.
     */

    /**
     * Observes a query and blocks only a confirmed reputation result.
     *
     * @param domain DNS hostname to evaluate.
     * @param customDay Compatibility parameter; ignored.
     * @return Tracker block for confirmed evidence, otherwise an allowed decision.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getCurrentPhase(customDay: Int? = null) = learningCurve.single()

    @Suppress("UNUSED_PARAMETER")
    fun evaluateDomain(
        domain: String,
        customDay: Int? = null,
    ): BlockingDecision {
        val analysis = networkLearner.analyzeQuery(domain)
        return if (analysis.state == ReputationState.CONFIRMED) {
            BlockingDecision(
                BlockingAction.BLOCK,
                analysis.reason,
                analysis.score,
                BlockingCategory.TRACKER,
                reasonCode = analysis.reasonCode,
            )
        } else {
            BlockingDecision.allow("Insufficient independent evidence", 1f - analysis.score)
        }
    }
}
