package com.unblocker.app.domain

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecideBlockingUseCaseTest {

    private lateinit var useCase: DecideBlockingUseCase
    private var adEnabled = true
    private var adultEnabled = true
    private var paused = false
    private var mode = BlockingMode.SMART
    private var allowlistedDomains = emptySet<String>()
    private var blocklistedDomains = emptySet<String>()
    private var myDomains = mutableSetOf<String>()

    @Before
    fun setup() {
        val adDetector = AdDetector(context = null).apply {
            addDomain("doubleclick.net")
            addDomain("adservice.google.com")
            addDomain("track.mybank.com")
        }
        val adultDetector = AdultContentDetector(context = null).apply {
            addDomain("pornhub.com")
            addDomain("xvideos.com")
        }
        val learner = LocalNetworkLearner()
        val adaptiveEngine = AdaptiveBlockingEngine(preferences = null, networkLearner = learner)
        val protectedDomains = DefaultProtectedDomains(
            builtIn = setOf("google.com", "duckduckgo.com"),
            myDomains = myDomains
        )

        useCase = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultDetector,
            adaptiveBlockingEngine = adaptiveEngine,
            isAllowlisted = { it in allowlistedDomains },
            isBlocklisted = { it in blocklistedDomains },
            isAdBlockingEnabled = { adEnabled },
            isAdultBlockingEnabled = { adultEnabled },
            isPaused = { paused },
            blockingMode = { mode },
            protectedDomains = protectedDomains
        )
    }

    @Test
    fun testBlockKnownAdNetworkWhenEnabled() {
        adEnabled = true
        val decision = useCase("doubleclick.net")
        assertTrue("doubleclick.net should be blocked", decision.isBlocked)
        assertEquals(BlockingAction.BLOCK, decision.action)
        assertEquals(BlockingCategory.AD, decision.category)
    }

    @Test
    fun testAllowKnownAdNetworkWhenDisabled() {
        adEnabled = false
        val decision = useCase("doubleclick.net")
        assertFalse("doubleclick.net should be allowed when ad blocking is disabled", decision.isBlocked)
        assertEquals(BlockingAction.ALLOW, decision.action)
    }

    @Test
    fun testBlockAdultWhenEnabled() {
        adultEnabled = true
        val decision = useCase("pornhub.com")
        assertTrue("pornhub.com should be blocked", decision.isBlocked)
        assertEquals(BlockingCategory.ADULT_CONTENT, decision.category)
    }

    @Test
    fun testAllowAdultWhenDisabled() {
        adultEnabled = false
        val decision = useCase("pornhub.com")
        assertFalse("pornhub.com should be allowed when adult blocking is disabled", decision.isBlocked)
    }

    @Test
    fun testAllowBenignDomains() {
        adEnabled = true
        adultEnabled = true

        val ytDecision = useCase("youtube.com")
        assertFalse("youtube.com should be allowed", ytDecision.isBlocked)

        val wikiDecision = useCase("wikipedia.org")
        assertFalse("wikipedia.org should be allowed", wikiDecision.isBlocked)
    }

    @Test
    fun testLocalAllowlistOverridesAdAndAdultRules() {
        allowlistedDomains = setOf("doubleclick.net", "pornhub.com")

        assertFalse(useCase("doubleclick.net").isBlocked)
        assertFalse(useCase("pornhub.com").isBlocked)
    }

    @Test
    fun testLocalBlocklistOverridesDisabledFilters() {
        adEnabled = false
        adultEnabled = false
        blocklistedDomains = setOf("wikipedia.org")

        val decision = useCase("wikipedia.org")

        assertTrue(decision.isBlocked)
        assertEquals(BlockingCategory.CUSTOM, decision.category)
    }

    @Test
    fun testAllowlistWinsWhenDomainExistsInBothLocalSets() {
        allowlistedDomains = setOf("example.org")
        blocklistedDomains = setOf("example.org")

        assertFalse(useCase("example.org").isBlocked)
    }

    @Test
    fun testPrimaryDomainInNeverBlockCannotBeBlockedByAdRules() {
        com.unblocker.app.logic.analysis.NeverBlockPolicy.injectForTest(
            setOf("duckduckgo.com", "google.com")
        )
        adEnabled = true
        val decision = useCase("duckduckgo.com")
        assertFalse("duckduckgo.com must never be blocked by ad rules", decision.isBlocked)
        assertEquals(BlockingAction.ALLOW, decision.action)

        val wwwDecision = useCase("www.duckduckgo.com")
        assertFalse("www.duckduckgo.com must never be blocked by ad rules", wwwDecision.isBlocked)
        assertEquals(BlockingAction.ALLOW, wwwDecision.action)
    }

    @Test
    fun testMyDomainsSubtreeProtection() {
        myDomains.add("mybank.com")
        // Rebuild useCase with updated myDomains
        setup()

        val decision = useCase("track.mybank.com")
        assertFalse("Subdomain of My Domains must be allowed despite matching ad rule", decision.isBlocked)
        assertEquals(DecisionReason.PROTECTED, decision.reasonCode)
    }

    @Test
    fun testApprovedOnlyModeDoesNotBlockLists() {
        mode = BlockingMode.APPROVED_ONLY
        setup()

        val decision = useCase("doubleclick.net")
        assertFalse("Approved only mode must not block list rules", decision.isBlocked)
        assertEquals(DecisionReason.NO_MATCH, decision.reasonCode)
    }

    @Test
    fun testPausedAllowsTraffic() {
        paused = true
        setup()

        val decision = useCase("doubleclick.net")
        assertFalse("Paused protection must allow traffic", decision.isBlocked)
        assertEquals(DecisionReason.PAUSED, decision.reasonCode)
    }

    @Test
    fun testDeofleadyetcaQuarantinedSeedBlocked() {
        val decision = useCase("deofleadyetca.org")
        assertTrue("Suspicious domain deofleadyetca.org must be blocked", decision.isBlocked)
        assertEquals(DecisionReason.STATIC_RULE, decision.reasonCode)
    }

    @Test
    fun testOffHeapRuleEngineIntegration() {
        val tempFile = java.io.File.createTempFile("rules_test", ".bin").apply { deleteOnExit() }
        val reader = java.io.BufferedReader(java.io.StringReader("||custom-tracker.com^\n0.0.0.0 bad-actor.xyz"))
        com.unblocker.data.cache.RuleCompiler.compileStreamsToBinaryFile(listOf(reader), tempFile)

        val offHeapEngine = com.unblocker.data.cache.OffHeapRuleEngine().apply {
            reloadFromFile(tempFile)
        }
        val customDetector = AdDetector(context = null, offHeapRuleEngine = offHeapEngine)
        val customUseCase = DecideBlockingUseCase(
            adDetector = customDetector,
            adultContentDetector = AdultContentDetector(context = null),
            adaptiveBlockingEngine = AdaptiveBlockingEngine(null, LocalNetworkLearner()),
            isAdBlockingEnabled = { true }
        )

        val directDecision = customUseCase("custom-tracker.com")
        assertTrue("Domain from OffHeapRuleEngine must be blocked", directDecision.isBlocked)

        val subDecision = customUseCase("sub.custom-tracker.com")
        assertTrue("Subdomain from OffHeapRuleEngine must be blocked", subDecision.isBlocked)

        val badActorDecision = customUseCase("bad-actor.xyz")
        assertTrue("Host from OffHeapRuleEngine must be blocked", badActorDecision.isBlocked)

        val safeDecision = customUseCase("legitimate-site.org")
        assertFalse("Unrelated domain must be allowed", safeDecision.isBlocked)
    }

    @Test
    fun testMobileUnbreakListAllowsEssentialAppDomains() {
        val supercellDecision = useCase("accounts.supercell.com")
        assertFalse("Supercell login must be allowed", supercellDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, supercellDecision.reasonCode)

        val spotifyDecision = useCase("login5.spotify.com")
        assertFalse("Spotify login must be allowed", spotifyDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, spotifyDecision.reasonCode)

        val playStoreDecision = useCase("prod-lt-playstoregatewayadapter-pa.googleapis.com")
        assertFalse("Play store adapter must be allowed", playStoreDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, playStoreDecision.reasonCode)

        val whatsappDecision = useCase("static.whatsapp.net")
        assertFalse("WhatsApp static CDN must be allowed", whatsappDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, whatsappDecision.reasonCode)
    }

    @Test
    fun testBasicTierPreservesRewardAndShoppingRedirects() {
        var currentTier = com.unblocker.app.data.preferences.FilteringTier.BASIC
        val adDetector = AdDetector(context = null).apply {
            addDomain("doubleclick.net")
            addDomain("googleadservices.com")
            addDomain("reward.unityads.unity3d.com")
        }
        val tieredUseCase = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = AdultContentDetector(context = null),
            adaptiveBlockingEngine = AdaptiveBlockingEngine(null, LocalNetworkLearner()),
            filteringTier = { currentTier }
        )

        // In BASIC mode, reward and shopping redirects are preserved
        val shoppingDecision = tieredUseCase("googleadservices.com")
        assertFalse("Shopping redirect must be allowed in BASIC mode", shoppingDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, shoppingDecision.reasonCode)

        val rewardDecision = tieredUseCase("reward.unityads.unity3d.com")
        assertFalse("Game reward ads must be allowed in BASIC mode", rewardDecision.isBlocked)
        assertEquals(DecisionReason.ALLOWED, rewardDecision.reasonCode)

        // Standard ad domains without reward/shopping function are still blocked
        val standardAd = tieredUseCase("doubleclick.net")
        assertTrue("Standard ad must be blocked even in BASIC mode", standardAd.isBlocked)

        // In OPTIMAL mode, reward and shopping redirects are blocked
        currentTier = com.unblocker.app.data.preferences.FilteringTier.OPTIMAL
        val optimalShopping = tieredUseCase("googleadservices.com")
        assertTrue("Shopping redirect must be blocked in OPTIMAL mode", optimalShopping.isBlocked)
    }
}
