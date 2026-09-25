package com.unblocker.app.data.model

data class FilterResult(
    val domain: String,
    val contentType: ContentType,
    val shouldBlock: Boolean,
    val reason: String,
    val detectionMethod: DetectionMethod,
    val confidence: Float = 1.0f
)
