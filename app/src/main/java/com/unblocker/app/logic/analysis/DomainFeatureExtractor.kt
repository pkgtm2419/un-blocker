package com.unblocker.app.logic.analysis

import kotlin.math.ln

/** Stateless features; PSL limits entropy to labels controlled by the registrant. */
class DomainFeatureExtractor(private val suffixes: PublicSuffixRules) {
    private val tokens = setOf("ad", "ads", "track", "tracker", "tracking", "telemetry", "pixel",
        "beacon", "analytics", "metrics", "banner", "affiliate", "sponsor", "adserver", "bidder")
    fun extract(domain: String, cadence: Float = 0f): DomainFeatures {
        val lexical = if (domain.split('.', '-', '_').any { it in tokens }) .8f else 0f
        val subdomain = suffixes.subdomain(domain)
        return DomainFeatures(lexical, entropy(subdomain), cadence,
            if (subdomain.count { it == '.' } >= 3 && lexical > 0) .65f else 0f)
    }
    private fun entropy(value: String): Float {
        if (value.length < 6 || value.contains("xn--")) return 0f
        val entropy = value.groupingBy { it }.eachCount().values.sumOf {
            val p = it.toDouble()/value.length
            -p * ln(p)/ln(2.0)
        }
        return when { entropy > 3.4 -> .85f; entropy > 3.1 -> .65f; entropy > 2.8 -> .35f; else -> 0f }
    }
}
