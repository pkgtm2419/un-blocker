package com.unblocker.app.services

/**
 * Synthetic domain expectation for a local policy smoke check.
 *
 * @property domain Hostname supplied to the evaluator.
 * @property shouldBlock Expected blocking result.
 */
data class BlockingRegressionCase(
    val domain: String,
    val shouldBlock: Boolean,
)

/**
 * Counts from a local synthetic smoke check.
 *
 * @property passed Number of matching expectations.
 * @property total Number of evaluated cases.
 */
data class BlockingRegressionResult(
    val passed: Int,
    val total: Int,
) {
    /**
     * Percentage of cases passed; zero total produces NaN when passed is also zero.
     */
    val passRate: Float = passed.toFloat() / total.toFloat() * 100f
}

/**
 * Deterministic local smoke check, without measuring real-world effectiveness.
 *
 * @param cases Nonempty synthetic expectations.
 * @throws IllegalArgumentException If cases is empty.
 */
class BlockingRegressionCheck(
    private val cases: List<BlockingRegressionCase>,
) {
    init {
        require(cases.isNotEmpty())
    }

    /**
     * Runs the supplied predicate for every case.
     *
     * @param shouldBlock Predicate returning the observed blocking decision for a hostname.
     * @return Passed and total case counts; predicate exceptions propagate.
     */
    fun evaluate(shouldBlock: (String) -> Boolean): BlockingRegressionResult {
        val passed = cases.count { case -> shouldBlock(case.domain) == case.shouldBlock }
        return BlockingRegressionResult(passed = passed, total = cases.size)
    }
}
