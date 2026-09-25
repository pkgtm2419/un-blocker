package com.unblocker.app.logic

import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalNetworkLearnerTest {

    private lateinit var learner: LocalNetworkLearner

    @Before
    fun setup() {
        learner = LocalNetworkLearner()
    }

    @Test
    fun testLexicalTokenDetection() {
        assertTrue("ads.example.com should be flagged by lexical analysis", learner.isAdOrTracker("ads.example.com"))
        assertTrue("pixel.tracker.net should be flagged by lexical analysis", learner.isAdOrTracker("pixel.tracker.net"))
        assertTrue("telemetry.vendor.org should be flagged by lexical analysis", learner.isAdOrTracker("telemetry.vendor.org"))
        assertTrue("adserver.metrics.io should be flagged by lexical analysis", learner.isAdOrTracker("adserver.metrics.io"))
    }

    @Test
    fun testShannonEntropyDetection() {
        // Normal domain: low entropy
        val normalEntropy = learner.evaluateEntropy("news.bbc.co.uk")
        assertTrue("Normal domain should have low entropy", normalEntropy < 0.6f)

        // Randomized machine subdomain typical of ad bidders
        val randomSubdomainEntropy = learner.evaluateEntropy("a8f9c2d7e1b4x9.adnxs.com")
        assertTrue("Algorithmic tracker subdomain should have elevated entropy", randomSubdomainEntropy >= 0.6f)
    }

    @Test
    fun testCadenceAndBurstDetection() {
        val testDomain = "api.burst-tracker-endpoint.com"
        val now = System.currentTimeMillis()

        // Simulate rapid burst of 5 queries within 200ms
        for (i in 0..4) {
            learner.analyzeQuery(testDomain)
        }

        val analysis = learner.analyzeQuery(testDomain)
        assertTrue("Rapid burst should trigger elevated score or tracker flag", analysis.score >= 0.5f)
    }

    @Test
    fun testSafeDomainBypass() {
        assertFalse("wikipedia.org should not be flagged", learner.isAdOrTracker("wikipedia.org"))
        assertFalse("github.com should not be flagged", learner.isAdOrTracker("github.com"))
        assertFalse("stackoverflow.com should not be flagged", learner.isAdOrTracker("stackoverflow.com"))
    }
}
