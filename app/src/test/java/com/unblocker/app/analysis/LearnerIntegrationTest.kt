package com.unblocker.app.analysis

import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression coverage for learner integration.
 */
class LearnerIntegrationTest {
    /**
     * Verifies that diagnostics do not observe or learn and unknown words do not hard block.
     */
    @Test fun diagnosticsDoNotObserveOrLearnAndUnknownWordsDoNotHardBlock() {
        val learner = LocalNetworkLearner()
        repeat(20) { learner.extractDomainSignature("ads.metrics.example.test") }
        assertEquals(0, learner.getLearnedTrackersCount())
        assertFalse(learner.isAdOrTracker("ads.example.test"))
    }

    /**
     * Verifies that calendar age does not relax the blocking threshold.
     */
    @Test fun calendarAgeDoesNotRelaxTheBlockingThreshold() {
        val engine = AdaptiveBlockingEngine(networkLearner = LocalNetworkLearner())
        assertEquals(engine.getCurrentPhase(1).confidenceThreshold, engine.getCurrentPhase(100).confidenceThreshold, 0f)
        assertFalse(engine.evaluateDomain("metrics.school.test", 100).isBlocked)
    }
}
