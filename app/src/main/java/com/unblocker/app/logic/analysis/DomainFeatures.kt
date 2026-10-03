package com.unblocker.app.logic.analysis

data class DomainFeatures(
    val lexical: Float = 0f, val entropy: Float = 0f, val cadence: Float = 0f,
    val structural: Float = 0f
)

data class ScoredFeatures(val score: Float, val mask: Int)
