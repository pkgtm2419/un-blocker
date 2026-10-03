package com.unblocker.app.logic

import org.junit.Assert.assertEquals
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
        assertFalse("Generic ad words are not deterministic authority", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("telemetry.vendor.net")
        assertFalse("Generic telemetry words are not deterministic authority", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("tracker.metrics.org")
        assertFalse("Generic tracker words are not deterministic authority", isAd3)
    }

    @Test
    fun testDoHCanaryDomain() {
        val (isCanary, reason) = adDetector.isAdDomain("use-application-dns.net")
        assertTrue("Firefox automatic DoH canary should receive the compatibility response", isCanary)
        assertEquals("Firefox DoH canary compatibility rule", reason)
        assertFalse(reason.contains("Chrome", ignoreCase = true))
        assertFalse(reason.contains("prevent", ignoreCase = true))
    }

    @Test
    fun testBrowserAdPatterns() {
        val (isAd1, _) = adDetector.isAdDomain("adserver.newsportal.com")
        assertFalse("Generic adserver names are only learning evidence", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("bidder.openx.net")
        assertTrue("bidder pattern should be detected", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("pixel.advertising.org")
        assertFalse("Generic pixel names are only learning evidence", isAd3)
    }

    @Test
    fun testMovieSitePopunderAndAdNetworksBlocked() {
        val (isAd1, _) = adDetector.isAdDomain("ds.recrampwiped.com")
        assertTrue("recrampwiped popunder must be blocked", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("sads.adsboosters.xyz")
        assertTrue("adsboosters popunder must be blocked", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("dh.hedeuntacks.com")
        assertTrue("hedeuntacks popunder must be blocked", isAd3)

        val (isAd4, _) = adDetector.isAdDomain("lp.legbaratwind.com")
        assertTrue("legbaratwind popunder must be blocked", isAd4)

        val (isAd5, _) = adDetector.isAdDomain("onclickads.net")
        assertTrue("onclickads propeller network must be blocked", isAd5)

        val (isAd6, _) = adDetector.isAdDomain("droplink.co")
        assertTrue("droplink ad gateway must be blocked", isAd6)
    }

    @Test
    fun testBenignDomainsNotBlocked() {
        val (isAd1, _) = adDetector.isAdDomain("wikipedia.org")
        assertFalse("wikipedia.org should not be detected as ad", isAd1)

        val (isAd2, _) = adDetector.isAdDomain("github.com")
        assertFalse("github.com should not be detected as ad", isAd2)

        val (isAd3, _) = adDetector.isAdDomain("stackoverflow.com")
        assertFalse("stackoverflow.com should not be detected as ad", isAd3)

        val (isAd4, _) = adDetector.isAdDomain("google.com")
        assertFalse("google.com base domain should not be detected as ad", isAd4)

        // Movie content domains must remain browsable by the user
        val (isMovie1, _) = adDetector.isAdDomain("uhdmovies.my")
        assertFalse("uhdmovies.my base site should not be classified as ad", isMovie1)

        val (isMovie2, _) = adDetector.isAdDomain("moviesmod.ai.in")
        assertFalse("moviesmod.ai.in base site should not be classified as ad", isMovie2)

        val (isMovie3, _) = adDetector.isAdDomain("gamesleech.com")
        assertFalse("gamesleech.com base site should not be classified as ad", isMovie3)
    }
}
