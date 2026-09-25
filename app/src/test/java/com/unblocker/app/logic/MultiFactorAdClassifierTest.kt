package com.unblocker.app.logic

import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiFactorAdClassifierTest {

    private lateinit var learner: LocalNetworkLearner

    @Before
    fun setup() {
        learner = LocalNetworkLearner()
    }

    @Test
    fun testDomainSignatureExtraction() {
        val signature = learner.extractDomainSignature("pixel.track.telemetry.adserver.com")

        assertEquals(4, signature.subdomainDepth)
        assertTrue("Should detect lexical ad token", signature.hasAdLexicalToken)
        assertTrue("Threat score should be elevated", signature.compositeThreatScore >= 0.70f)
    }

    @Test
    fun testDynamicDnsTrackerDetection() {
        val ddnsDomain = "tracker-beacon.duckdns.org"
        val signature = learner.extractDomainSignature(ddnsDomain)

        assertTrue("Should detect dynamic DNS provider", signature.isDdnsOrDynamic)
        assertTrue("Should detect lexical tracker token", signature.hasAdLexicalToken)
        assertTrue("Threat score should be elevated for DDNS tracker", signature.compositeThreatScore >= 0.75f)
    }

    @Test
    fun testKnownMobileTrackerFrameworks() {
        val trackers = listOf(
            "adjust.com", "appsflyer.com", "branch.io", "kochava.com",
            "mixpanel.com", "segment.com", "amplitude.com", "scorecardresearch.com"
        )

        for (tracker in trackers) {
            val sig = learner.extractDomainSignature(tracker)
            assertTrue("Should detect $tracker as known tracker signature", sig.hasTrackerSignature)
            assertTrue("Should assign high threat score to $tracker", sig.compositeThreatScore >= 0.90f)
        }
    }

    @Test
    fun testBenignDomainSignature() {
        val signature = learner.extractDomainSignature("en.wikipedia.org")

        assertEquals(2, signature.subdomainDepth)
        assertFalse("Wikipedia has no ad tokens", signature.hasAdLexicalToken)
        assertFalse("Wikipedia is not DDNS", signature.isDdnsOrDynamic)
        assertFalse("Wikipedia is not a tracker framework", signature.hasTrackerSignature)
        assertEquals(0.0f, signature.compositeThreatScore, 0.001f)
    }
}
