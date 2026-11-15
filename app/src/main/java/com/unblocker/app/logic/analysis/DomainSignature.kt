package com.unblocker.app.logic.analysis

/**
 * Diagnostic snapshot of structural and local classifier signals.
 *
 * @property domain Normalized hostname, or empty for invalid input.
 * @property subdomainDepth Number of dots in the normalized hostname, including the registrable portion.
 * @property hasAdLexicalToken Whether the lexical feature crosses the diagnostic threshold.
 * @property hasTrackerSignature Whether the configured known-tracker predicate matches.
 * @property isDdnsOrDynamic Whether the hostname matches the diagnostic dynamic-DNS suffixes.
 * @property entropy Heuristic entropy score, rather than raw bits of entropy.
 * @property cadenceScore Retained query-cadence score.
 * @property suspiciousPatterns Diagnostic pattern descriptions; current extractor supplies an empty list.
 * @property compositeThreatScore Known-rule or combined heuristic score on a zero-to-one scale.
 */
data class DomainSignature(
    val domain: String,
    val subdomainDepth: Int,
    val hasAdLexicalToken: Boolean,
    val hasTrackerSignature: Boolean,
    val isDdnsOrDynamic: Boolean,
    val entropy: Float,
    val cadenceScore: Float,
    val suspiciousPatterns: List<String>,
    val compositeThreatScore: Float,
)
