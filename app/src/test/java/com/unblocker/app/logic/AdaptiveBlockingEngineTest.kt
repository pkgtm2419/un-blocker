package com.unblocker.app.logic

import com.unblocker.app.domain.model.*
import com.unblocker.app.logic.analysis.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveBlockingEngineTest {
    @Test fun ageCannotChangeEvidenceThresholdOrClassification() {
        val engine=AdaptiveBlockingEngine()
        for(day in listOf(1,2,7,14,100)) {
            assertEquals(.8f,engine.getCurrentPhase(day).confidenceThreshold,0f)
            assertFalse(engine.evaluateDomain("metrics.school.test",day).isBlocked)
            assertFalse(engine.evaluateDomain("github.com",day).isBlocked)
            assertTrue(engine.evaluateDomain("adjust.com",day).isBlocked)
            assertEquals(DecisionReason.STATIC_RULE,engine.evaluateDomain("adjust.com",day).reasonCode)
        }
    }
    @Test fun independentEvidenceConfirmsButSingleBurstDoesNot() {
        var now=0L
        val learner=LocalNetworkLearner(clock={now})
        val engine=AdaptiveBlockingEngine(networkLearner=learner)
        repeat(8) { now+=50; assertFalse(engine.evaluateDomain("ads.example.test").isBlocked) }
        now+=60_001
        repeat(3) { now+=50; assertFalse(engine.evaluateDomain("ads.example.test").isBlocked) }
        val confirmed=engine.evaluateDomain("ads.example.test")
        assertTrue(confirmed.isBlocked)
        assertEquals(BlockingCategory.TRACKER,confirmed.category)
        assertEquals(DecisionReason.LEARNED_EVIDENCE,confirmed.reasonCode)
    }
}
