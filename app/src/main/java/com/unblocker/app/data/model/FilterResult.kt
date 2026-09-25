package com.unblocker.app.data.model

enum class ContentType {
    AD,
    ADULT_CONTENT,
    NORMAL
}

enum class DetectionMethod {
    STATIC_LIST,
    PATTERN_MATCH,
    TLD_RULE,
    HEURISTIC_ANALYSIS,
    NONE
}

data class FilterResult(
    val domain: String,
    val contentType: ContentType,
    val shouldBlock: Boolean,
    val reason: String,
    val detectionMethod: DetectionMethod,
    val confidence: Float = 1.0f
)
