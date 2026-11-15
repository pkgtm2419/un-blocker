package com.unblocker.app.quality

import com.unblocker.ml.classifier.HeuristicDomainClassifier
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for runtime inference availability.
 */
class RuntimeInferenceAvailabilityTest {
    /**
     * Verifies that heuristic classifier scores ad domain higher than benign.
     */
    @Test
    fun heuristicClassifierScoresAdDomainHigherThanBenign() {
        val classifier = HeuristicDomainClassifier()
        val adScore = classifier.predictAdProbability("tracking.adserver-pixel.example.com")
        val benignScore = classifier.predictAdProbability("wikipedia.org")
        assertTrue(
            "Ad domain score ($adScore) must be higher than benign domain score ($benignScore)",
            adScore > benignScore,
        )
    }
}
