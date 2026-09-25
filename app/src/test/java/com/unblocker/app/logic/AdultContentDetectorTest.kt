package com.unblocker.app.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdultContentDetectorTest {

    private lateinit var adultDetector: AdultContentDetector

    @Before
    fun setup() {
        adultDetector = AdultContentDetector(context = null)
        adultDetector.addDomain("pornhub.com")
        adultDetector.addDomain("xvideos.com")
        adultDetector.addDomain("xnxx.com")
        adultDetector.addDomain("chaturbate.com")
        adultDetector.addDomain("livejasmin.com")
    }

    @Test
    fun testExactAdultDomainMatch() {
        val (isAdult1, _) = adultDetector.isAdultContent("pornhub.com")
        assertTrue("pornhub.com should be detected as adult content", isAdult1)

        val (isAdult2, _) = adultDetector.isAdultContent("xvideos.com")
        assertTrue("xvideos.com should be detected as adult content", isAdult2)
    }

    @Test
    fun testSubdomainMatch() {
        val (isAdult, _) = adultDetector.isAdultContent("m.pornhub.com")
        assertTrue("m.pornhub.com should match parent adult domain", isAdult)

        val (isAdult2, _) = adultDetector.isAdultContent("cdn.chaturbate.com")
        assertTrue("cdn.chaturbate.com should match adult domain", isAdult2)
    }

    @Test
    fun testAdultTldRule() {
        val (isAdult1, _) = adultDetector.isAdultContent("example-gallery.xxx")
        assertTrue(".xxx TLD should be identified as adult content", isAdult1)

        val (isAdult2, _) = adultDetector.isAdultContent("members-portal.adult")
        assertTrue(".adult TLD should be identified as adult content", isAdult2)

        val (isAdult3, _) = adultDetector.isAdultContent("streaming.porn")
        assertTrue(".porn TLD should be identified as adult content", isAdult3)
    }

    @Test
    fun testAdultPatternMatch() {
        val (isAdult1, _) = adultDetector.isAdultContent("free-porn-videos.com")
        assertTrue("Domain with porn pattern should be identified as adult", isAdult1)

        val (isAdult2, _) = adultDetector.isAdultContent("live-cam-strip.net")
        assertTrue("Domain with strip/cam pattern should be identified as adult", isAdult2)
    }

    @Test
    fun testFalsePositiveGuard() {
        val (isAdult1, _) = adultDetector.isAdultContent("essex.ac.uk")
        assertFalse("essex.ac.uk must NOT be blocked as adult content", isAdult1)

        val (isAdult2, _) = adultDetector.isAdultContent("sussex.ac.uk")
        assertFalse("sussex.ac.uk must NOT be blocked as adult content", isAdult2)

        val (isAdult3, _) = adultDetector.isAdultContent("middlesex.edu")
        assertFalse("middlesex.edu must NOT be blocked as adult content", isAdult3)
    }

    @Test
    fun testBenignDomainsNotBlocked() {
        val (isAdult1, _) = adultDetector.isAdultContent("google.com")
        assertFalse("google.com must not be identified as adult", isAdult1)

        val (isAdult2, _) = adultDetector.isAdultContent("bbc.com")
        assertFalse("bbc.com must not be identified as adult", isAdult2)

        val (isAdult3, _) = adultDetector.isAdultContent("apple.com")
        assertFalse("apple.com must not be identified as adult", isAdult3)
    }
}
