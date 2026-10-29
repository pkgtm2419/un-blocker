package com.unblocker.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainNameTest {
    @Test fun normalizesCaseWhitespaceAndDnsRootDot() {
        assertEquals("ads.example.com", DomainName.normalize("  Ads.Example.COM.  "))
    }

    @Test fun rejectsNonDnsAndMalformedInputs() {
        val invalid = listOf(
            "https://ads.example.com",
            "ads.example.com:443",
            "ads..example.com",
            "-ads.example.com",
            "ads-.example.com",
            "ads/example.com",
            "ads\u0000.example.com",
            "💀.example.com",
            "${"a".repeat(64)}.example.com",
            "${"a".repeat(250)}.com"
        )

        invalid.forEach { value -> assertNull(value, DomainName.normalize(value)) }
    }

    @Test fun detectorsUseTheSameCanonicalDomainBoundary() {
        val adDetector = AdDetector().apply { addDomain("doubleclick.net") }
        val adultDetector = AdultContentDetector().apply { addDomain("blocked.example") }

        assertTrue(adDetector.isAdDomain("DOUBLECLICK.NET.").first)
        assertTrue(adultDetector.isAdultContent("Blocked.Example.").first)
        assertFalse(adDetector.isAdDomain("https://doubleclick.net").first)
        assertFalse(adultDetector.isAdultContent("blocked.example:443").first)
    }

    @Test fun normalizeUserEntryConvertsInternationalizedDomainsToPunycode() {
        assertEquals("xn--mnchen-3ya.de", DomainName.normalizeUserEntry("  München.DE.  "))
        assertEquals("xn--bcher-kva.example.com", DomainName.normalizeUserEntry("bücher.example.com"))
        assertEquals("ads.example.com", DomainName.normalizeUserEntry("  ads.example.com.  "))
        assertEquals("xn--mnchen-3ya.de", DomainName.normalizeUserEntry("xn--mnchen-3ya.de"))

        assertNull(DomainName.normalizeUserEntry("https://münchen.de"))
        assertNull(DomainName.normalizeUserEntry("münchen..de"))
        assertNull(DomainName.normalizeUserEntry("-münchen.de"))
    }

    @Test fun detectorsSupportIdnaPunycodeCanonicalizationForUserAddedDomains() {
        val adDetector = AdDetector().apply { addDomain("münchen-ads.de") }
        val adultDetector = AdultContentDetector().apply { addDomain("schön.com") }

        // Wire DNS queries come in Punycode (ASCII)
        assertTrue(adDetector.isAdDomain("xn--mnchen-ads-9db.de").first)
        assertTrue(adDetector.isAdDomain("sub.xn--mnchen-ads-9db.de").first)
        assertTrue(adultDetector.isAdultContent("xn--schn-7qa.com").first)
        assertTrue(adultDetector.isAdultContent("sub.xn--schn-7qa.com").first)
    }
}
