package com.unblocker.ml.analyzer

/**
 * Extracts overlapping character n-grams from normalized text.
 *
 * Normalization lowercases text and keeps letters, digits, and hyphens.
 *
 * @param n Gram width in characters; positive values are expected but not validated at construction.
 */
class NgramTokenizer(private val n: Int = 3) {

    /**
     * Normalizes text and extracts consecutive n-grams in order.
     *
     * @param input Text to normalize; dots and other excluded characters are removed.
     * @return Overlapping grams, or an empty list when normalized text is shorter than [n].
     * @throws IndexOutOfBoundsException If a negative [n] yields invalid substring bounds.
     */
    fun tokenize(input: String): List<String> {
        val clean = input.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        if (clean.length < n) return emptyList()
        return (0..clean.length - n).map { i -> clean.substring(i, i + n) }
    }

    /**
     * Counts normalized n-grams and divides each count by the total number of grams.
     *
     * @param input Text normalized by [tokenize].
     * @return Relative gram frequencies, or an empty map when no grams are produced.
     * @throws IndexOutOfBoundsException If a negative [n] yields invalid substring bounds.
     */
    fun frequencyVector(input: String): Map<String, Float> {
        val ngrams = tokenize(input)
        if (ngrams.isEmpty()) return emptyMap()
        val counts = ngrams.groupingBy { it }.eachCount()
        val total = ngrams.size.toFloat()
        return counts.mapValues { (_, count) -> count / total }
    }
}
