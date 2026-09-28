package com.unblocker.app.domain

import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
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
    private var allowlistedDomains = emptySet<String>()
    private var blocklistedDomains = emptySet<String>()

    @Before
    fun setup() {
        val adDetector = AdDetector(context = null).apply {
            addDomain("doubleclick.net")
            addDomain("adservice.google.com")
        }
        val adultDetector = AdultContentDetector(context = null).apply {
            addDomain("pornhub.com")
            addDomain("xvideos.com")
        }
        val learner = LocalNetworkLearner()
        val adaptiveEngine = AdaptiveBlockingEngine(preferences = null, networkLearner = learner)

        useCase = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultDetector,
            adaptiveBlockingEngine = adaptiveEngine,
            isAllowlisted = { it in allowlistedDomains },
            isBlocklisted = { it in blocklistedDomains },
            isAdBlockingEnabled = { adEnabled },
            isAdultBlockingEnabled = { adultEnabled }
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
}
