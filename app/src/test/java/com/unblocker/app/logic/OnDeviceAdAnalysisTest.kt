package com.unblocker.app.logic

import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.analysis.*
import org.junit.Assert.*
import org.junit.Test

class OnDeviceAdAnalysisTest {

    private var testTime = 100_000L
    private val adDetector = AdDetector(ruleSet = com.unblocker.app.logic.rules.RuleSet.empty())
    private val adultDetector = AdultContentDetector()
    private val networkLearner = LocalNetworkLearner(clock = { testTime })
    private val adaptiveEngine = AdaptiveBlockingEngine(null, networkLearner)
    private val pipeline = DecideBlockingUseCase(
        adDetector = adDetector,
        adultContentDetector = adultDetector,
        adaptiveBlockingEngine = adaptiveEngine,
        isAdBlockingEnabled = { true },
        isAdultBlockingEnabled = { false }
    )

    @Test
    fun testHighConfidenceUnlistedAdDomainsAreAutoBlocked() {
        // Domains with explicit ad tokens and high entropy/nesting not in any static list
        val unlistedAds = listOf(
            "hash987f6e5d4c3b2a1.adserver.dynamic-ads.org",
            "telemetry.pixel.tracker-bidding-exchange.com",
            "adclick.statcounter.analytics-collector.net",
            "bidder.rtb.sub789xyz.adsystem-host.biz"
        )

        for (domain in unlistedAds) {
            // Window 1: Initial observation starts tracking without premature blocking
            val initialDecision = pipeline(domain)
            assertFalse("Domain should not be prematurely blocked on a single query: $domain", initialDecision.isBlocked)

            // Advance time past the 60-second minimum window separation
            testTime += 65_000L

            // Window 2: Corroborated evidence promotes to CONFIRMED on-device
            val confirmedDecision = pipeline(domain)
            assertTrue("Expected on-device auto-blocking after corroboration for $domain", confirmedDecision.isBlocked)
            assertEquals("Blocking action must be BLOCK", BlockingAction.BLOCK, confirmedDecision.action)
            assertTrue("Reason must reflect on-device autonomous analysis: ${confirmedDecision.reason}",
                confirmedDecision.reason.contains("Autonomous") || confirmedDecision.reason.contains("Local evidence"))
        }
    }

    @Test
    fun testNeverBlockPolicyShieldsCriticalDomains() {
        val protectedDomains = listOf(
            "connectivitycheck.gstatic.com",
            "connectivitycheck.android.com",
            "clients3.google.com",
            "google.com",
            "www.google.com",
            "accounts.google.com",
            "play.google.com",
            "apple.com",
            "microsoft.com",
            "github.com",
            "api.github.com",
            "pypi.org"
        )

        for (domain in protectedDomains) {
            assertTrue("NeverBlockPolicy must recognize $domain", NeverBlockPolicy.isNeverBlock(domain))
            val analysis = networkLearner.analyzeQuery(domain)
            assertEquals("Reputation must be SUPPRESSED for protected domain $domain",
                ReputationState.SUPPRESSED, analysis.state)
            val decision = pipeline(domain)
            assertFalse("Protected domain $domain must NEVER be blocked", decision.isBlocked)
            assertEquals("Protected infrastructure", decision.reason)
        }
    }

    @Test
    fun testBenignLookalikesRemainAllowed() {
        val benignDomains = listOf(
            "cdn.example.org",
            "static.shop.net",
            "api.service.io",
            "user-portal.company.com",
            "docs.devplatform.io"
        )

        for (domain in benignDomains) {
            val decision = pipeline(domain)
            assertFalse("Benign domain $domain must remain allowed", decision.isBlocked)
        }
    }

    @Test
    fun testExcessiveDomainLengthFailsSafely() {
        val longDomain = "a".repeat(250) + ".example.com"
        val features = DomainFeatureExtractor(PublicSuffixRules.from(null)).extract(longDomain)
        assertEquals(0f, features.lexical, 0f)
        assertEquals(0f, features.entropy, 0f)
        val decision = pipeline(longDomain)
        assertFalse(decision.isBlocked)
    }
}
