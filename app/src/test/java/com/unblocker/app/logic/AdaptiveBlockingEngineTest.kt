package com.unblocker.app.logic

import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.BlockingStrategy
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveBlockingEngineTest {

    private lateinit var adaptiveEngine: AdaptiveBlockingEngine

    @Before
    fun setup() {
        val learner = LocalNetworkLearner()
        adaptiveEngine = AdaptiveBlockingEngine(preferences = null, networkLearner = learner)
    }

    @Test
    fun testSevenDayLearningCurvePhases() {
        val phases = adaptiveEngine.learningCurve
        assertEquals("Should contain 7 learning curve phases", 7, phases.size)

        // Day 1: Strict whitelist (0.95 threshold)
        assertEquals(1, phases[0].day)
        assertEquals(0.95f, phases[0].confidenceThreshold, 0.001f)
        assertEquals(BlockingStrategy.STRICT_WHITELIST, phases[0].blockingStrategy)

        // Day 2: Known ads & patterns (0.90)
        assertEquals(2, phases[1].day)
        assertEquals(0.90f, phases[1].confidenceThreshold, 0.001f)

        // Day 3: Heuristic analysis (0.85)
        assertEquals(3, phases[2].day)
        assertEquals(0.85f, phases[2].confidenceThreshold, 0.001f)

        // Day 4: Progressive adaptive (0.80)
        assertEquals(4, phases[3].day)
        assertEquals(0.80f, phases[3].confidenceThreshold, 0.001f)

        // Day 5: Progressive adaptive (0.78)
        assertEquals(5, phases[4].day)
        assertEquals(0.78f, phases[4].confidenceThreshold, 0.001f)

        // Day 6: Progressive adaptive (0.76)
        assertEquals(6, phases[5].day)
        assertEquals(0.76f, phases[5].confidenceThreshold, 0.001f)

        // Day 7+: Autonomous adaptive (0.75)
        assertEquals(7, phases[6].day)
        assertEquals(0.75f, phases[6].confidenceThreshold, 0.001f)
        assertEquals(BlockingStrategy.AGGRESSIVE_ADAPTIVE, phases[6].blockingStrategy)
    }

    @Test
    fun testDayClampingBeyondSevenDays() {
        val day10Phase = adaptiveEngine.getCurrentPhase(customDay = 10)
        assertEquals("Day 10 should clamp to Day 7+ aggressive adaptive phase", 7, day10Phase.day)
        assertEquals(0.75f, day10Phase.confidenceThreshold, 0.001f)
    }

    @Test
    fun testDayOneZeroFalsePositiveBehavior() {
        val phaseDay1 = adaptiveEngine.getCurrentPhase(customDay = 1)
        assertEquals(0.95f, phaseDay1.confidenceThreshold, 0.001f)

        // Major internet websites must never be blocked
        val ytDecision = adaptiveEngine.evaluateDomain("youtube.com", customDay = 1)
        assertFalse("youtube.com must NOT be blocked on Day 1", ytDecision.isBlocked)

        val wikiDecision = adaptiveEngine.evaluateDomain("wikipedia.org", customDay = 1)
        assertFalse("wikipedia.org must NOT be blocked on Day 1", wikiDecision.isBlocked)

        val netflixDecision = adaptiveEngine.evaluateDomain("netflix.com", customDay = 1)
        assertFalse("netflix.com must NOT be blocked on Day 1", netflixDecision.isBlocked)
    }

    @Test
    fun testAdaptiveBlockingOnKnownTrackers() {
        // Known mobile tracker framework should be blocked across phases
        val decisionDay1 = adaptiveEngine.evaluateDomain("adjust.com", customDay = 1)
        assertTrue("adjust.com should be blocked as known tracker", decisionDay1.isBlocked)
        assertEquals(BlockingCategory.TRACKER, decisionDay1.category)

        val decisionDay7 = adaptiveEngine.evaluateDomain("appsflyer.com", customDay = 7)
        assertTrue("appsflyer.com should be blocked on Day 7", decisionDay7.isBlocked)
    }

    @Test
    fun testSafeDomainsAlwaysProtectedAcrossAllPhases() {
        val safeDomains = listOf(
            "youtube.com", "googlevideo.com", "google.com", "netflix.com",
            "spotify.com", "apple.com", "microsoft.com", "github.com"
        )

        for (day in 1..7) {
            for (domain in safeDomains) {
                val decision = adaptiveEngine.evaluateDomain(domain, customDay = day)
                assertFalse("$domain must NOT be blocked on Day $day", decision.isBlocked)
            }
        }
    }
}
