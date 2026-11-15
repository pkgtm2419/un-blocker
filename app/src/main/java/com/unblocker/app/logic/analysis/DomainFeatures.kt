package com.unblocker.app.logic.analysis

/**
 * Heuristic feature strengths, conventionally on a zero-to-one scale.
 *
 * @property lexical Advertising-token signal in subdomain labels.
 * @property entropy Score derived from subdomain character entropy.
 * @property cadence Query timing signal supplied by the cadence tracker.
 * @property structural Suspicious subdomain nesting signal.
 */
data class DomainFeatures(
    val lexical: Float = 0f,
    val entropy: Float = 0f,
    val cadence: Float = 0f,
    val structural: Float = 0f,
)

/**
 * Combined heuristic result.
 *
 * @property score Corroborated heuristic score on a zero-to-one scale.
 * @property mask Bit flags: lexical 1, entropy 2, cadence 4 and structural 8.
 */
data class ScoredFeatures(
    val score: Float,
    val mask: Int,
)
