package com.unblocker.ml.classifier

import com.unblocker.ml.analyzer.DomainFeatureExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic on-device scoring of keyword, entropy, and domain structure features.
 */
@Singleton
class HeuristicDomainClassifier
    @Inject
    constructor() : DomainClassifier {
        private companion object {
            const val ENTROPY_INDEX = 1
            const val HYPHEN_COUNT_INDEX = 3
            const val SUBDOMAIN_DEPTH_INDEX = 4
            const val KEYWORD_FLAG_INDEX = 5
            const val ENTROPY_SIGNAL_INDEX = 6
        }

        /**
         * Combines fixed weights over the extracted feature vector.
         *
         * @param domain Domain text normalized by the feature extractor.
         * @return Heuristic score clamped to the inclusive range from 0 to 1.
         */
        override fun predictAdProbability(domain: String): Float {
            val features = DomainFeatureExtractor.extractFeatures(domain)
            // features: [0] len, [1] entropy, [2] digit ratio, [3] hyphens, [4] subdomain depth,
            //           [5] keywordHit, [6] entropySignal, [7] labelCount, [8] longestLabel
            val keywordScore = if (features[KEYWORD_FLAG_INDEX] > 0.5f) 0.70f else 0.0f
            val entropyScore = features[ENTROPY_INDEX] * 0.15f + features[ENTROPY_SIGNAL_INDEX] * 0.10f
            val structureScore = (features[SUBDOMAIN_DEPTH_INDEX] * 0.05f) + (features[HYPHEN_COUNT_INDEX] * 0.05f)
            return (keywordScore + entropyScore + structureScore).coerceIn(0f, 1f)
        }

        /**
         * Completes without work because this classifier owns no interpreter or native resources.
         */
        override fun close() {}
    }
