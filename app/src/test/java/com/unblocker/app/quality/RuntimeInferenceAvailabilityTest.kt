package com.unblocker.app.quality

import com.unblocker.ml.classifier.HeuristicDomainClassifier
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInferenceAvailabilityTest {
    @Test
    fun heuristicClassifierScoresAdDomainHigherThanBenign() {
        val classifier = HeuristicDomainClassifier()
        val adScore = classifier.predictAdProbability("tracking.adserver-pixel.example.com")
        val benignScore = classifier.predictAdProbability("wikipedia.org")
        assertTrue(
            "Ad domain score ($adScore) must be higher than benign domain score ($benignScore)",
            adScore > benignScore
        )
    }
}
