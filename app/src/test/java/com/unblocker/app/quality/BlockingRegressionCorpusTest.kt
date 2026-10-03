package com.unblocker.app.quality

import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A deterministic regression corpus, not a claim about real-world blocking percentages. */
class BlockingRegressionCorpusTest {
    private val classifier = DecideBlockingUseCase(
        adDetector = AdDetector(context = null),
        adultContentDetector = AdultContentDetector(context = null),
        adaptiveBlockingEngine = AdaptiveBlockingEngine(
            preferences = null,
            networkLearner = LocalNetworkLearner()
        ),
        isAdBlockingEnabled = { true },
        isAdultBlockingEnabled = { false }
    )

    @Test fun representativeAdAndTrackerEndpointsRemainBlocked() {
        val blocked = listOf(
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "pagead2.googlesyndication.com",
            "admob.com",
            "sdk.applovin.com",
            "unityads.unity3d.com",
            "api.vungle.com",
            "ads.inmobi.com",
            "adjust.com",
            "api.appsflyer.com",
            "branch.io",
            "kochava.com",
            "flurry.com",
            "mixpanel.com",
            "segment.io",
            "amplitude.com",
            "hotjar.com",
            "clarity.ms"
        )

        blocked.forEach { domain ->
            assertTrue("Expected regression corpus to block $domain", classifier(domain).isBlocked)
        }
    }

    @Test fun majorLegitimateServicesRemainAllowed() {
        val allowed = listOf(
            "google.com",
            "github.com",
            "stackoverflow.com",
            "wikipedia.org",
            "youtube.com",
            "googlevideo.com",
            "amazon.com",
            "aws.amazon.com",
            "apple.com",
            "cloudflare.com",
            "microsoft.com",
            "netflix.com",
            "reddit.com",
            "spotify.com",
            "linkedin.com",
            "instagram.com",
            "facebook.com",
            "fastly.net",
            "akamai.net",
            "twitch.tv"
        )

        allowed.forEach { domain ->
            assertFalse("Expected regression corpus to allow $domain", classifier(domain).isBlocked)
        }
    }

    @Test fun lexicalOnlyUnknownNamesRemainAllowed() {
        listOf("telemetry.vendor.example", "analytics.vendor.example", "metrics.vendor.example",
            "tracker.vendor.example").forEach {
            assertFalse("A word alone cannot authorize blocking $it", classifier(it).isBlocked)
        }
    }
}
