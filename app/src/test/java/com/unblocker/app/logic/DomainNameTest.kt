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
}
