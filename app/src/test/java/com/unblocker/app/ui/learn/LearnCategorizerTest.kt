package com.unblocker.app.ui.learn

import com.unblocker.app.logic.analysis.PublicSuffixRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying whole-label DNS categorization and shared-platform whole-site limits (WP-03).
 */
class LearnCategorizerTest {
    private val psl = PublicSuffixRules.fallback

    @Test
    fun threadsDownloadsAndBroadcastAreNotClassifiedAsTrackersBySubstring() {
        assertEquals(
            DomainCategory.OTHER,
            LearnReviewPolicy.categorizeDomain("threads.net", "com.example.news", psl),
        )
        assertEquals(
            DomainCategory.OTHER,
            LearnReviewPolicy.categorizeDomain("downloads.example.com", "com.other.app", psl),
        )
        assertEquals(
            DomainCategory.OTHER,
            LearnReviewPolicy.categorizeDomain("broadcast.example.com", "com.other.app", psl),
        )
    }

    @Test
    fun wholeLabelTrackerMatchesAreClassifiedAsTrackerAd() {
        assertEquals(
            DomainCategory.TRACKER_AD,
            LearnReviewPolicy.categorizeDomain("ads.example.com", "com.other.app", psl),
        )
        assertEquals(
            DomainCategory.TRACKER_AD,
            LearnReviewPolicy.categorizeDomain("telemetry.example.com", "com.other.app", psl),
        )
        assertEquals(
            DomainCategory.TRACKER_AD,
            LearnReviewPolicy.categorizeDomain("analytics.vendor.com", "com.other.app", psl),
        )
        assertEquals(
            DomainCategory.TRACKER_AD,
            LearnReviewPolicy.categorizeDomain("doubleclick.net", "com.google.android.gm", psl),
        )
    }

    @Test
    fun activeBlocklistMatchesAreClassifiedAsTrackerAd() {
        val category =
            LearnReviewPolicy.categorizeDomain(
                domain = "custom-tracker.example.net",
                targetPackage = "com.example.app",
                publicSuffixRules = psl,
                isBlockedByRules = { it == "custom-tracker.example.net" },
            )
        assertEquals(DomainCategory.TRACKER_AD, category)
    }

    @Test
    fun firstPartyRequiresWholeDnsLabelMatchNotSubstringContainment() {
        // Segment "red" should not substring-match "redirect.example.com"
        assertEquals(
            DomainCategory.OTHER,
            LearnReviewPolicy.categorizeDomain("redirect.example.com", "com.red.reader", psl),
        )
        // Exact label match qualifies as FIRST_PARTY
        assertEquals(
            DomainCategory.FIRST_PARTY,
            LearnReviewPolicy.categorizeDomain("api.spotify.com", "com.spotify.music", psl),
        )
    }

    @Test
    fun cdnSuffixMatchesExactOrSubdomainWithoutFalseSubstringSuffix() {
        assertEquals(
            DomainCategory.CDN_INFRA,
            LearnReviewPolicy.categorizeDomain("d1234.cloudfront.net", "com.example.app", psl),
        )
        assertEquals(
            DomainCategory.OTHER,
            LearnReviewPolicy.categorizeDomain("mycloudfront.net", "com.example.app", psl),
        )
    }

    @Test
    fun sharedPlatformsDisableWholeSitePromotion() {
        assertFalse(LearnReviewPolicy.canAllowWholeSite("d123.cloudfront.net", psl))
        assertFalse(LearnReviewPolicy.canAllowWholeSite("mybucket.s3.amazonaws.com", psl))
        assertFalse(LearnReviewPolicy.canAllowWholeSite("fonts.googleapis.com", psl))
        assertFalse(LearnReviewPolicy.canAllowWholeSite("myblog.github.io", psl))
        assertFalse(LearnReviewPolicy.canAllowWholeSite("doubleclick.net", psl))
        assertTrue(LearnReviewPolicy.canAllowWholeSite("api.mynewsapp.com", psl))
    }
}
