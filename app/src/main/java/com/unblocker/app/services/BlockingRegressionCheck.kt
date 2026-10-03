package com.unblocker.app.services

data class BlockingRegressionCase(val domain: String, val shouldBlock: Boolean)

data class BlockingRegressionResult(val passed: Int, val total: Int) {
    val passRate: Float = passed.toFloat() / total.toFloat() * 100f
}

/** Deterministic local smoke check. This is not a measurement of real-world effectiveness. */
class BlockingRegressionCheck(private val cases: List<BlockingRegressionCase>) {
    init {
        require(cases.isNotEmpty())
    }

    fun evaluate(shouldBlock: (String) -> Boolean): BlockingRegressionResult {
        val passed = cases.count { case -> shouldBlock(case.domain) == case.shouldBlock }
        return BlockingRegressionResult(passed = passed, total = cases.size)
    }
}
