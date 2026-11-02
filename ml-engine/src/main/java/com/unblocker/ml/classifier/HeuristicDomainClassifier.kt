package com.unblocker.ml.classifier

import com.unblocker.ml.analyzer.DomainFeatureExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure on-device deterministic heuristic domain classifier.
 * Eliminates the ~14.7 MB native TensorFlow Lite runtime dependency while maintaining
 * feature scoring across entropy, keyword hits, and subdomain structures.
 */
@Singleton
class HeuristicDomainClassifier @Inject constructor() : DomainClassifier {
    override fun predictAdProbability(domain: String): Float {
        val features = DomainFeatureExtractor.extractFeatures(domain)
        // features: [0] len, [1] entropy, [2] digit ratio, [3] hyphens, [4] subdomain depth,
        //           [5] keywordHit, [6] entropySignal, [7] labelCount, [8] longestLabel
        val keywordScore = if (features[5] > 0.5f) 0.70f else 0.0f
        val entropyScore = features[1] * 0.15f + features[6] * 0.10f
        val structureScore = (features[4] * 0.05f) + (features[3] * 0.05f)
        return (keywordScore + entropyScore + structureScore).coerceIn(0f, 1f)
    }

    override fun close() {}
}
