package com.unblocker.ml.analyzer

/**
 * Extracts nine bounded heuristic features from a domain string.
 *
 * Indices are length/50, registrant-label entropy/5, registrant digit ratio, registrant hyphens/5,
 * subdomain depth/4, keyword flag, entropy signal, label count/6, and longest-label length/30.
 * The registrant label is approximated as the penultimate label without public-suffix parsing.
 * Keyword matching includes substrings of the full domain. This is a heuristic feature layout,
 * not evidence of a bundled trained model or a replacement for the separate app extractor.
 */
object DomainFeatureExtractor {

    /**
     * Number of elements emitted by [extractFeatures], with stable feature indices 0 through 8.
     */
    const val FEATURE_SIZE = 9

    /** Heuristic tokens checked as full-domain substrings and split-label parts. */
    private val SUSPICIOUS_TOKENS = setOf(
        "ad", "ads", "adv", "adserver", "adservice", "adsystem", "adtech", "adclick",
        "adlog", "pagead", "track", "tracker", "tracking", "telemetry", "pixel",
        "beacon", "analytics", "metrics", "metric", "stat", "banner", "affiliate",
        "sponsor", "bidder", "rtb", "ssp", "dsp", "sdk", "click", "counter",
        "popunder", "statcounter", "impression", "conversion", "retarget"
    )

    /**
     * Extracts normalized features after lowercasing and removing trailing dots.
     *
     * @param domain Domain text; validity and public-suffix policy are not checked here.
     * @return New nine-element array with the layout documented on [DomainFeatureExtractor].
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
     * Calculates the same entropy value used by the feature extractor.
     *
     * @param input Text to measure using [EntropyCalculator] exclusions.
     * @return Entropy in bits, or zero for short text or text containing the punycode prefix.
     */
    fun calculateShannonEntropy(input: String): Float = EntropyCalculator.calculate(input)
}
