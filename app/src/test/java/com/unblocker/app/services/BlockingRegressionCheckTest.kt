package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockingRegressionCheckTest {
    @Test fun evaluatesBothBlockedAndAllowedExpectations() {
        val cases = listOf(
            BlockingRegressionCase("ads.example", shouldBlock = true),
            BlockingRegressionCase("tracker.example", shouldBlock = true),
            BlockingRegressionCase("bank.example", shouldBlock = false),
            BlockingRegressionCase("docs.example", shouldBlock = false)
        )
        val blocked = setOf("ads.example", "tracker.example")

        val result = BlockingRegressionCheck(cases).evaluate { it in blocked }

        assertEquals(4, result.passed)
        assertEquals(4, result.total)
        assertEquals(100f, result.passRate, 0f)
    }

    @Test fun reportsFailuresAsRegressionRateNotEffectiveness() {
        val cases = listOf(
            BlockingRegressionCase("ads.example", shouldBlock = true),
            BlockingRegressionCase("bank.example", shouldBlock = false)
        )

        val result = BlockingRegressionCheck(cases).evaluate { false }

        assertEquals(1, result.passed)
        assertEquals(2, result.total)
        assertEquals(50f, result.passRate, 0f)
    }
}
