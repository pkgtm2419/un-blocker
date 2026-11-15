package com.unblocker.common.model

/**
 * Domain rule and provenance, without normalization or range validation.
 *
 * @property domain Domain expected to be lowercase without a trailing dot.
 * @property confidenceScore Confidence conventionally from 0 to 1; static rules default to 1.
 * @property ruleSource Source responsible for the rule.
 */
data class DomainRecord(
    val domain: String,
    val confidenceScore: Float = 1.0f,
    val ruleSource: RuleSource = RuleSource.STATIC,
)
