package com.unblocker.app.logic

import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertEquals
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
        listOf("ads.example.com", "pixel.tracker.net", "telemetry.vendor.org", "adserver.metrics.io").forEach {
            assertTrue(learner.evaluateLexical(it)>.5f)
            assertFalse("Lexical evidence alone cannot confirm",learner.isAdOrTracker(it))
        }
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
        val testDomain = "tracker.burst-endpoint.com"
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

        // YouTube & video CDN endpoints
        assertFalse("youtube.com should not be flagged", learner.isAdOrTracker("youtube.com"))
        assertFalse("www.youtube.com should not be flagged", learner.isAdOrTracker("www.youtube.com"))
        assertFalse("m.youtube.com should not be flagged", learner.isAdOrTracker("m.youtube.com"))
        assertFalse("googlevideo.com should not be flagged", learner.isAdOrTracker("googlevideo.com"))
        assertFalse("rr1---sn-4g5ednle.googlevideo.com should not be flagged", learner.isAdOrTracker("rr1---sn-4g5ednle.googlevideo.com"))
        assertFalse("i.ytimg.com should not be flagged", learner.isAdOrTracker("i.ytimg.com"))

        // Explicit ad subdomains MUST still be blocked
        assertFalse("Unknown ad words require corroboration", learner.isAdOrTracker("ads.google.com"))
        assertFalse("Unknown ad words require corroboration", learner.isAdOrTracker("ads.youtube.com"))
    }

    @Test
    fun malformedDomainSyntaxNeverBecomesLearnedTracker() {
        assertFalse(learner.isAdOrTracker("ads..example.com"))
        assertFalse(learner.isAdOrTracker("ads/example.com"))
        assertFalse(learner.isAdOrTracker("ads.example.com:443"))

        val signature = learner.extractDomainSignature("ads..example.com")
        assertEquals("", signature.domain)
        assertEquals(0.0f, signature.compositeThreatScore, 0.0f)
    }
}
