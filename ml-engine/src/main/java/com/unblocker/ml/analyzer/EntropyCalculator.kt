package com.unblocker.ml.analyzer

import kotlin.math.ln

/**
 * Character-distribution Shannon entropy and a fixed heuristic confidence mapping.
 *
 * Entropy is one feature; high entropy alone does not establish that a domain is a tracker.
 */
object EntropyCalculator {

    /**
     * Calculates Shannon entropy in bits from character frequencies.
     *
     * @param input Case-sensitive text; strings containing lowercase xn-- are excluded.
     * @return Entropy in bits, or zero for fewer than six characters or the punycode exclusion.
     */
    fun calculate(input: String): Float {
        if (input.length < 6 || input.contains("xn--")) return 0f

        val frequencies = input.groupingBy { it }.eachCount()
        val length = input.length.toDouble()

        val entropy = frequencies.values.sumOf { count ->
            val probability = count.toDouble() / length
            -probability * ln(probability) / ln(2.0)
        }

        return entropy.toFloat()
    }

    /**
     * Maps entropy to a fixed heuristic signal using strict thresholds.
     *
     * @param entropy Shannon entropy in bits.
     * @return 0.85 above 3.4, 0.65 above 3.1, 0.35 above 2.8, or zero otherwise.
     */
    fun toSignal(entropy: Float): Float = when {
        entropy > 3.4f -> 0.85f
        entropy > 3.1f -> 0.65f
        entropy > 2.8f -> 0.35f
        else           -> 0.0f
    }
}
