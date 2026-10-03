package com.unblocker.app.logic.analysis

class HeuristicScorer {
    fun score(features: DomainFeatures): ScoredFeatures {
        var mask = 0
        if (features.lexical >= .5f) mask = mask or 1
        if (features.entropy >= .6f) mask = mask or 2
        if (features.cadence >= .6f) mask = mask or 4
        if (features.structural >= .6f) mask = mask or 8
        // Entropy/cadence alone are common on benign CDNs. Require lexical corroboration.
        val positive = mask and 1 != 0 && Integer.bitCount(mask) >= 2
        return ScoredFeatures(if (positive) .8f else if (mask != 0) .4f else 0f, mask)
    }

    /**
     * Identifies immediate high-confidence ad/tracker domains where an explicit ad token
     * is strongly corroborated by high entropy (randomized tracking hashes) or structural nesting.
     */
    fun isHighConfidenceAd(features: DomainFeatures): Boolean {
        return features.lexical >= 0.8f && (features.entropy >= 0.65f || features.structural >= 0.65f)
    }
}
