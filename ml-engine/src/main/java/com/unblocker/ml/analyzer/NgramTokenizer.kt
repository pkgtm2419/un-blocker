package com.unblocker.ml.analyzer

/**
 * Extracts character-level n-gram frequency vectors from a domain label string.
 *
 * N-grams are overlapping sub-sequences of length [n] from the input. They capture
 * structural patterns in domain labels that a TFLite text-classification model uses
 * as dense features — for example, "ads", "trk", "pxl" frequently appear in ad/tracker
 * domains regardless of surrounding context.
 *
 * ## Example
 * ```
 * "adserver" with n=3 → ["ads", "dse", "ser", "erv", "rve", "ver"]
 * ```
 *
 * @param n  The n-gram size. Default 3 (trigrams) — optimal for domain label classification.
 */
class NgramTokenizer(private val n: Int = 3) {

    /**
     * Tokenizes [input] into a list of n-gram strings.
     *
     * @param input  A single domain label (no dots). Lowercased automatically.
     * @return       Ordered list of overlapping n-grams. Empty if [input].length < [n].
     */
    fun tokenize(input: String): List<String> {
        val clean = input.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        if (clean.length < n) return emptyList()
        return (0..clean.length - n).map { i -> clean.substring(i, i + n) }
    }

    /**
     * Returns a frequency map of n-grams → count, normalised by total n-gram count.
     * Used as a sparse feature vector for TFLite models that accept float[] tensors.
     *
     * @param input  Domain label string.
     * @return       Map of n-gram → relative frequency (0.0–1.0).
     */
    fun frequencyVector(input: String): Map<String, Float> {
        val ngrams = tokenize(input)
        if (ngrams.isEmpty()) return emptyMap()
        val counts = ngrams.groupingBy { it }.eachCount()
        val total = ngrams.size.toFloat()
        return counts.mapValues { (_, count) -> count / total }
    }
}
