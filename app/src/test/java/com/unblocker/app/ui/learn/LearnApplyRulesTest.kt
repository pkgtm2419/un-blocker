package com.unblocker.app.ui.learn

import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.services.RecordingSessionManager
import com.unblocker.app.services.SessionDomain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying initial zero-selection defaults, exact ALLOW submission, shared-platform
 * safeguards, ad-domain refusal, and cross-session history lookup in [LearnReviewPolicy] (WP-03).
 */
class LearnApplyRulesTest {
    private val psl = PublicSuffixRules.fallback

    private class RecordingGateway : UserRuleGateway {
        val exactCalls = mutableListOf<Pair<String, UserFeedback>>()
        val wholeSiteCalls = mutableListOf<Pair<String, UserFeedback>>()

        override suspend fun set(
            domain: String,
            feedback: UserFeedback,
        ): RuleUpdateResult {
            exactCalls.add(domain to feedback)
            return RuleUpdateResult.Applied
        }

        override suspend fun setWholeSite(
            domain: String,
            feedback: UserFeedback,
        ): RuleUpdateResult {
            wholeSiteCalls.add(domain to feedback)
            return RuleUpdateResult.Applied
        }

        override suspend fun protectDomain(domain: String): RuleUpdateResult = RuleUpdateResult.Applied

        override suspend fun reset(domain: String): RuleUpdateResult = RuleUpdateResult.Applied
    }

    @After
    fun tearDown() {
        RecordingSessionManager.instance.stopSession()
        RecordingSessionManager.instance.clearHistoryForTesting()
    }

    @Test
    fun initialReviewListDefaultsEveryRowToKeepDefaultWithExactScope() {
        val recorded =
            listOf(
                SessionDomain("api.google.com", count = 5),
                SessionDomain("d123.cloudfront.net", count = 3),
                SessionDomain("googleads.g.doubleclick.net", count = 2),
                SessionDomain("threads.net", count = 1),
            )

        val reviewed =
            LearnReviewPolicy.buildInitialReviewList(
                recorded = recorded,
                targetPackage = "com.google.android.apps.docs",
                publicSuffixRules = psl,
            )

        assertEquals(4, reviewed.size)
        assertTrue(reviewed.all { it.action == ReviewAction.KEEP_DEFAULT })
        assertTrue(reviewed.all { !it.allowWholeSite })
    }

    @Test
    fun allowSubmitsExactHostByDefaultAndWholeSiteOnlyWhenToggledOnNonShared() =
        runBlocking {
            val gateway = RecordingGateway()
            val items =
                listOf(
                    ReviewedDomain(
                        domain = "api.mynewsapp.com",
                        count = 2,
                        category = DomainCategory.FIRST_PARTY,
                        action = ReviewAction.ALLOW,
                        allowWholeSite = false,
                        canAllowWholeSite = true,
                    ),
                    ReviewedDomain(
                        domain = "static.mynewsapp.com",
                        count = 1,
                        category = DomainCategory.FIRST_PARTY,
                        action = ReviewAction.ALLOW,
                        allowWholeSite = true,
                        canAllowWholeSite = true,
                    ),
                    // Even if caller forcibly passes allowWholeSite=true on shared host, guard keeps exact
                    ReviewedDomain(
                        domain = "fonts.googleapis.com",
                        count = 1,
                        category = DomainCategory.CDN_INFRA,
                        action = ReviewAction.ALLOW,
                        allowWholeSite = true,
                        canAllowWholeSite = false,
                    ),
                )

            val outcome = LearnReviewPolicy.applyReviewedRules(items, gateway, psl)

            assertEquals(3, outcome.appliedCount)
            assertTrue(outcome.refusedAdDomains.isEmpty())
            assertEquals(
                listOf(
                    "api.mynewsapp.com" to UserFeedback.ALLOW,
                    "fonts.googleapis.com" to UserFeedback.ALLOW,
                ),
                gateway.exactCalls,
            )
            assertEquals(
                listOf("static.mynewsapp.com" to UserFeedback.ALLOW),
                gateway.wholeSiteCalls,
            )
        }

    @Test
    fun adDomainAllowRequiresSecondExplicitConfirmationAndGoogleSessionCannotAllowDoubleclick() =
        runBlocking {
            val gateway = RecordingGateway()

            assertNotNull(LearnReviewPolicy.adAllowRiskWarning("googleads.g.doubleclick.net", psl))
            assertNotNull(LearnReviewPolicy.adAllowRiskWarning("pagead2.googlesyndication.com", psl))
            assertNotNull(LearnReviewPolicy.adAllowRiskWarning("googleadservices.com", psl))
            assertNotNull(LearnReviewPolicy.adAllowRiskWarning("static.2mdn.net", psl))
            assertNotNull(LearnReviewPolicy.adAllowRiskWarning("ib.adnxs.com", psl))
            assertNull(LearnReviewPolicy.adAllowRiskWarning("api.mynewsapp.com", psl))

            // Attempting to submit unconfirmed ALLOW on doubleclick.net is refused
            val unconfirmedAd =
                ReviewedDomain(
                    domain = "googleads.g.doubleclick.net",
                    count = 4,
                    category = DomainCategory.TRACKER_AD,
                    action = ReviewAction.ALLOW,
                    confirmedAdAllow = false,
                )
            val confirmedAd =
                ReviewedDomain(
                    domain = "ad.doubleclick.net",
                    count = 1,
                    category = DomainCategory.TRACKER_AD,
                    action = ReviewAction.ALLOW,
                    confirmedAdAllow = true,
                )

            val outcome =
                LearnReviewPolicy.applyReviewedRules(
                    listOf(unconfirmedAd, confirmedAd),
                    gateway,
                    psl,
                )

            assertEquals(1, outcome.appliedCount)
            assertEquals(listOf("googleads.g.doubleclick.net"), outcome.refusedAdDomains)
            assertEquals(listOf("ad.doubleclick.net" to UserFeedback.ALLOW), gateway.exactCalls)
            assertTrue(gateway.wholeSiteCalls.isEmpty())
        }

    @Test
    fun alsoSeenInTracksDomainsAcrossFinishedLearnSessions() {
        val manager = RecordingSessionManager.instance
        manager.clearHistoryForTesting()

        manager.startSession("com.app.alpha")
        manager.recordDomain("shared.cdn.example.com")
        manager.recordDomain("alpha.only.example.com")
        manager.stopSession()

        manager.startSession("com.app.beta")
        manager.recordDomain("shared.cdn.example.com")
        manager.stopSession()

        assertEquals(
            listOf("com.app.alpha"),
            manager.getOtherAppsSeeingDomain("shared.cdn.example.com", "com.app.beta"),
        )
        assertTrue(
            manager.getOtherAppsSeeingDomain("alpha.only.example.com", "com.app.alpha").isEmpty(),
        )
    }
}
