package com.unblocker.app.rules

import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.rules.CompiledRuleSet
import com.unblocker.app.logic.rules.RuleAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VendorUnblockTest {
    private lateinit var detector: AdDetector

    @Before
    fun setup() {
        val tsvFile = File("build/generated/dns-assets/dns-rules.tsv")
        assertTrue("dns-rules.tsv must exist", tsvFile.exists())
        val rules = tsvFile.reader().use { CompiledRuleSet.fromTsv(it) }
        detector = AdDetector(compiledRules = rules)
    }

    @Test
    fun testVendorWebsitesAndDashboardsAllowed() {
        val allowedVendorHosts = listOf(
            "amplitude.com", "www.amplitude.com",
            "appsflyer.com", "www.appsflyer.com",
            "applovin.com", "www.applovin.com",
            "branch.io", "www.branch.io",
            "adjust.com", "www.adjust.com",
            "kochava.com", "www.kochava.com",
            "flurry.com", "www.flurry.com",
            "singular.net", "www.singular.net",
            "braze.com", "www.braze.com",
            "mixpanel.com", "www.mixpanel.com",
            "segment.com", "www.segment.com",
            "segment.io", "www.segment.io",
            "clarity.ms", "www.clarity.ms",
            "hotjar.com", "www.hotjar.com",
            "newrelic.com", "www.newrelic.com"
        )

        for (host in allowedVendorHosts) {
            val (isBlocked, reason) = detector.isAdDomain(host)
            assertFalse("Vendor website $host must be allowed but was blocked: $reason", isBlocked)
            val match = detector.matchRule(host)
            assertEquals("Rule for $host should be ALLOW", RuleAction.ALLOW, match?.action)
        }
    }

    @Test
    fun testVendorSdkEndpointsBlocked() {
        val blockedEndpoints = listOf(
            "api.amplitude.com",
            "t.appsflyer.com",
            "api.branch.io",
            "app.adjust.com",
            "api.kochava.com",
            "data.flurry.com",
            "api.singular.net",
            "api.mixpanel.com",
            "api.segment.io",
            "c.clarity.ms",
            "script.hotjar.com",
            "mobile-collector.newrelic.com"
        )

        for (endpoint in blockedEndpoints) {
            val (isBlocked, reason) = detector.isAdDomain(endpoint)
            assertTrue("Vendor SDK endpoint $endpoint must be blocked: $reason", isBlocked)
            val match = detector.matchRule(endpoint)
            assertEquals("Rule for $endpoint should be BLOCK", RuleAction.BLOCK, match?.action)
        }
    }
}
