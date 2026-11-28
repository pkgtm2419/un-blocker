package com.unblocker.app.data.model

import com.unblocker.app.domain.model.DecisionReason

/**
 * Content classification assigned by the domain filtering engine.
 */
enum class ContentType {
    AD,
    ADULT_CONTENT,
    CUSTOM,
    NORMAL,
}

/**
 * Rule or heuristic family credited with the domain classification.
 */
enum class DetectionMethod {
    STATIC_LIST,
    PATTERN_MATCH,
    TLD_RULE,
    HEURISTIC_ANALYSIS,
    CUSTOM_RULE,
    NONE,
}

/**
 * Classification, rule attribution, and decision returned by domain filtering.
 *
 * @property domain Domain associated with the decision.
 * @property contentType Content category selected by the detector.
 * @property shouldBlock Whether the query should receive a blocked response.
 * @property reason Explanation associated with the decision.
 * @property detectionMethod Rule or heuristic family used to classify the domain.
 * @property confidence Detector-provided confidence score, conventionally from zero to one.
 * @property reasonCode Granular machine-readable decision reason code (WP-21).
 * @property matchedRule Specific rule hostname or pattern matched, or blank when none.
 * @property sourceLabel List/policy attribution tag for decision explanation UI.
 */
data class FilterResult(
    val domain: String,
    val contentType: ContentType,
    val shouldBlock: Boolean,
    val reason: String,
    val detectionMethod: DetectionMethod,
    val confidence: Float = 1.0f,
    val reasonCode: DecisionReason = DecisionReason.ALLOWED,
    val matchedRule: String = "",
    val sourceLabel: String = "",
)
