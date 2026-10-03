package com.unblocker.app.logic

import com.unblocker.app.logic.analysis.DomainFeatureExtractor
import com.unblocker.app.logic.analysis.PublicSuffixRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFeatureExtractorTest {
    private val suffixRules = PublicSuffixRules.fallback
    private val extractor = DomainFeatureExtractor(suffixRules)

    @Test
    fun testRegistrableLabelMatchingPrevented() {
        // Domains where the token is part of the registrable domain or ccTLD, not the subdomain
        val benignRegistrableDomains = listOf(
            "ad.co.uk",
            "ad.com",
            "ads.net",
            "track.org",
            "analytics.com",
            "banner.com",
            "sponsor.org",
            "pixel.com"
        )

        for (domain in benignRegistrableDomains) {
            val features = extractor.extract(domain)
            assertEquals("Registrable domain token in $domain must not score lexical", 0f, features.lexical, 0.001f)
        }
    }

    @Test
    fun testSubdomainTokensTriggerLexical() {
        val trackerSubdomains = listOf(
            "ad.example.com",
            "ads.example.net",
            "track.customer.org",
            "tracker.service.com",
            "analytics.vendor.com",
            "adservice.cdn.com",
            "adsystem.network.com",
            "adtech.platform.org",
            "pagead.host.com",
            "rtb.exchange.com",
            "ssp.adnetwork.com",
            "dsp.bidder.com",
            "sdk-api.telemetry.com"
        )

        for (domain in trackerSubdomains) {
            val features = extractor.extract(domain)
            assertTrue("Subdomain token in $domain must score lexical", features.lexical >= 0.5f)
        }
    }
}
