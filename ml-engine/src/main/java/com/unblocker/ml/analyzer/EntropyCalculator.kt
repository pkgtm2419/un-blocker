package com.unblocker.ml.analyzer

import kotlin.math.ln

/**
 * Calculates the Shannon entropy of a string using natural log (base-2 normalised).
 *
 * Shannon entropy measures the unpredictability / randomness of character distribution.
 * High-entropy domain labels (e.g., "a8f3d92c.cdn.example.com") are characteristic of
 * algorithmically-generated tracking identifiers and DGA (domain-generation algorithm) domains.
 *
 * ## Interpretation scale
 * | Entropy | Interpretation                                      |
 * |---------|-----------------------------------------------------|
 * | < 2.8   | Low — human-readable label (e.g., "google")         |
 * | 2.8–3.1 | Moderate — short random suffix (e.g., "ads123")    |
 * | 3.1–3.4 | High — partial hash / tracking ID                  |
 * | > 3.4   | Very high — full UUID / DGA hash                   |
 *
 * The thresholds above are calibrated against the existing app's [HeuristicScorer].
 *
 * @param input  The domain label or full domain string to measure.
 * @return       Normalised entropy in bits (0.0f = fully predictable, higher = more random).
 */
object EntropyCalculator {

    /**
     * Calculates Shannon entropy H(X) = -Σ p(x) * log₂(p(x)) for [input].
     *
     * Punycode-encoded labels ("xn--") are excluded — their encoded form inflates entropy
     * artificially and does not indicate tracking behaviour.
     *
     * @return Entropy in bits as a [Float]. Returns 0f for inputs shorter than 6 characters
     *         (too short for a meaningful entropy signal) or Punycode labels.
     */
    fun calculate(input: String): Float {
        if (input.length < 6 || input.contains("xn--")) return 0f

        val frequencies = input.groupingBy { it }.eachCount()
        val length = input.length.toDouble()

        val entropy = frequencies.values.sumOf { count ->
            val p = count.toDouble() / length
            -p * ln(p) / ln(2.0)
        }

        return entropy.toFloat()
    }

    /**
     * Maps a raw entropy value to a normalised confidence signal (0.0–1.0)
     * using the same thresholds calibrated in the existing [HeuristicScorer].
     *
     * @return 0.85f for very high entropy (>3.4), down to 0f for low entropy.
     */
    fun toSignal(entropy: Float): Float = when {
        entropy > 3.4f -> 0.85f
        entropy > 3.1f -> 0.65f
        entropy > 2.8f -> 0.35f
        else           -> 0.0f
    }
}
