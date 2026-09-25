package com.unblocker.app.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdDetectorTest {

    private lateinit var adDetector: AdDetector

    @Before
    fun setup() {
        // Without Android context, tests fallback + pattern matching + manual additions
        adDetector = AdDetector(context = null)
        adDetector.addDomain("doubleclick.net")
        adDetector.addDomain("googleads.g.doubleclick.net")
        adDetector.addDomain("adservice.google.com")
        adDetector.addDomain("applovin.com")
        adDetector.addDomain("unityads.unity3d.com")
        adDetector.addDomain("inmobi.com")
        adDetector.addDomain("criteo.com")
    }

    @Test
    fun testExactDomainMatch() {
        val (isAd1, reason1) = adDetector.isAdDomain("doubleclick.net")
        assertTrue("doubleclick.net should be detected as ad", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("applovin.com")
        assertTrue("applovin.com should be detected as ad", isAd2)
    }

    @Test
    fun testSubdomainSuffixMatch() {
        val (isAd, _) = adDetector.isAdDomain("sub.doubleclick.net")
        assertTrue("sub.doubleclick.net should match parent doubleclick.net", isAd)

        val (isAd2, _) = adDetector.isAdDomain("deep.nested.applovin.com")
        assertTrue("deep.nested.applovin.com should match applovin.com", isAd2)
    }

    @Test
    fun testPatternMatch() {
        val (isAd1, _) = adDetector.isAdDomain("ads.randomwebsite.com")
        assertTrue("ads.randomwebsite.com should match ad pattern", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("telemetry.vendor.net")
        assertTrue("telemetry.vendor.net should match telemetry pattern", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("tracker.metrics.org")
        assertTrue("tracker.metrics.org should match tracker pattern", isAd3)
    }

    @Test
    fun testBenignDomainsNotBlocked() {
        val (isAd1, _) = adDetector.isAdDomain("wikipedia.org")
        assertFalse("wikipedia.org should not be detected as ad", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("github.com")
        assertFalse("github.com should not be detected as ad", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("stackoverflow.com")
        assertFalse("stackoverflow.com should not be detected as ad", isAd3)
    }
}
