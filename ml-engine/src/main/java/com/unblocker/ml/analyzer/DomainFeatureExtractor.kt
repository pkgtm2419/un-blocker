package com.unblocker.ml.analyzer

import kotlin.math.log2

/**
 * Extracts a fixed-length normalised feature vector from a domain name for TFLite inference.
 *
 * ## Feature vector layout (indices 0–8)
 * | Index | Feature               | Normalisation       | Signal                                     |
 * |-------|-----------------------|---------------------|--------------------------------------------|
 * | 0     | Total domain length   | ÷ 50                | Long domains → more likely generated       |
 * | 1     | Shannon entropy       | ÷ 5                 | High entropy → randomised tracking label   |
 * | 2     | Digit ratio           | raw [0.0–1.0]       | Lots of digits → ID/hash segment           |
 * | 3     | Hyphen count          | ÷ 5                 | Hyphens common in ad-network subdomains    |
 * | 4     | Subdomain depth       | ÷ 4                 | Deep nesting → tracking proxy chain        |
 * | 5     | Keyword flag          | 0 or 1              | Exact ad/tracker token present             |
 * | 6     | Entropy signal        | see EntropyCalculator| Binned entropy confidence signal          |
 * | 7     | Label count           | ÷ 6                 | Total label count                          |
 * | 8     | Longest label ratio   | raw [0.0–1.0]       | Unusually long single label                |
 *
 * The vector is designed to be directly consumable as the input tensor for the bundled
 * `domain_classifier.tflite` model (`input_shape=[1, 9]`).
 *
 * ## Design note
 * This class supersedes the legacy [com.unblocker.app.logic.analysis.DomainFeatureExtractor]
 * in the `:app` module. It extends the feature set with entropy signal and structural depth
 * to improve recall on CDN-hosted ad trackers.
 */
object DomainFeatureExtractor {

    const val FEATURE_SIZE = 9

    /**
     * Suspicious token set — present in the majority of known ad/tracking domain labels.
     * Sourced from EasyList and AdGuard blocklist label analysis.
     */
    private val SUSPICIOUS_TOKENS = setOf(
        "ad", "ads", "adv", "adserver", "adservice", "adsystem", "adtech", "adclick",
        "adlog", "pagead", "track", "tracker", "tracking", "telemetry", "pixel",
        "beacon", "analytics", "metrics", "metric", "stat", "banner", "affiliate",
        "sponsor", "bidder", "rtb", "ssp", "dsp", "sdk", "click", "counter",
        "popunder", "statcounter", "impression", "conversion", "retarget"
    )

    /**
     * Extracts a normalised [FloatArray] of length [FEATURE_SIZE] from [domain].
     *
     * @param domain  Fully-qualified domain name (lowercased, no trailing dot required).
     * @return        Feature vector ready for TFLite `Interpreter.run()`.
     */
    fun extractFeatures(domain: String): FloatArray {
        val clean = domain.lowercase().trimEnd('.')
        val labels = clean.split('.')
        val registrantLabel = if (labels.size >= 2) labels[labels.size - 2] else clean
        val subdomainLabels = if (labels.size > 2) labels.dropLast(2) else emptyList()

        val domainLength    = clean.length.toFloat()
        val entropy         = EntropyCalculator.calculate(registrantLabel)
        val digitRatio      = registrantLabel.count { it.isDigit() }.toFloat() /
                              registrantLabel.length.coerceAtLeast(1).toFloat()
        val hyphens         = registrantLabel.count { it == '-' }.toFloat()
        val subdomainDepth  = subdomainLabels.size.toFloat()
        val keywordHit      = if (hasKeyword(clean, labels)) 1f else 0f
        val entropySignal   = EntropyCalculator.toSignal(entropy)
        val labelCount      = labels.size.toFloat()
        val longestLabel    = labels.maxOfOrNull { it.length }?.toFloat() ?: 0f

        return floatArrayOf(
            (domainLength / 50f).coerceIn(0f, 1f),      // [0] Domain length
            (entropy / 5f).coerceIn(0f, 1f),            // [1] Entropy
            digitRatio.coerceIn(0f, 1f),                // [2] Digit ratio
            (hyphens / 5f).coerceIn(0f, 1f),            // [3] Hyphen count
            (subdomainDepth / 4f).coerceIn(0f, 1f),     // [4] Subdomain depth
            keywordHit,                                  // [5] Keyword flag
            entropySignal,                               // [6] Entropy signal
            (labelCount / 6f).coerceIn(0f, 1f),         // [7] Label count
            (longestLabel / 30f).coerceIn(0f, 1f)       // [8] Longest label ratio
        )
    }

    /**
     * Checks whether [domain] or any of its [labels] contain a suspicious keyword token.
     * Splits hyphenated labels to catch "ad-server.example.com" style patterns.
     */
    private fun hasKeyword(domain: String, labels: List<String>): Boolean {
        // Direct substring check on full domain first (fast path)
        for (token in SUSPICIOUS_TOKENS) {
            if (domain.contains(token)) return true
        }
        // Split on hyphen to catch "ad-click", "track-er"
        for (label in labels) {
            val parts = label.split('-', '_')
            for (part in parts) {
                if (part in SUSPICIOUS_TOKENS) return true
            }
        }
        return false
    }

    /**
     * Convenience: calculate Shannon entropy directly (delegates to [EntropyCalculator]).
     * Kept for backwards compatibility with tests referencing this object.
     */
    fun calculateShannonEntropy(input: String): Float = EntropyCalculator.calculate(input)
}
