package com.unblocker.common.model

/**
 * Represents a domain record stored in the filtering rule tables.
 *
 * @param domain          Fully-qualified domain name (lowercase, no trailing dot).
 * @param confidenceScore ML confidence score (0.0–1.0). 1.0 for static rules.
 * @param ruleSource      Origin of the rule: [RuleSource.STATIC], [RuleSource.ML], [RuleSource.USER].
 */
data class DomainRecord(
    val domain: String,
    val confidenceScore: Float = 1.0f,
    val ruleSource: RuleSource = RuleSource.STATIC
)
