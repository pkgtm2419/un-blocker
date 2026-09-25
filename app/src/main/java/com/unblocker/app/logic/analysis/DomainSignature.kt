package com.unblocker.app.logic.analysis

/**
 * Multi-factor domain signature extraction for on-device network analysis.
 * Encapsulates structural, lexical, entropy, cadence, and reputation indicators
 * as specified in Section 1.1.1 of un-blocker-improvement-plan.md.
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
    val compositeThreatScore: Float
)
