package com.unblocker.app.logic

import com.unblocker.app.logic.analysis.NeverBlockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for never block policy.
 */
class NeverBlockPolicyTest {

    /**
     * Resets the fixture state before each test to keep assertions independent.
     */
    @Before
    fun setup() {
        // Inject test set of protected hosts
        NeverBlockPolicy.injectForTest(
            setOf(
                "connectivitycheck.gstatic.com",
                "*.google.com",
                "time.android.com",
                "dns.google"
            )
        )
    }

    /**
     * Verifies that test never block exact match.
     */
    @Test
    fun testNeverBlockExactMatch() {
        assertTrue(NeverBlockPolicy.isNeverBlock("connectivitycheck.gstatic.com"))
        assertTrue(NeverBlockPolicy.isNeverBlock("time.android.com"))
        assertTrue(NeverBlockPolicy.isNeverBlock("dns.google"))
    }

    /**
     * Verifies that test never block wildcard match.
     */
    @Test
    fun testNeverBlockWildcardMatch() {
        assertTrue(NeverBlockPolicy.isNeverBlock("mail.google.com"))
        assertTrue(NeverBlockPolicy.isNeverBlock("www.google.com"))
    }

    /**
     * Verifies that test unprotected domain returns false.
     */
    @Test
    fun testUnprotectedDomainReturnsFalse() {
        assertFalse(NeverBlockPolicy.isNeverBlock("doubleclick.net"))
        assertFalse(NeverBlockPolicy.isNeverBlock("adservice.google.com.malicious.org"))
        assertFalse(NeverBlockPolicy.isNeverBlock("tracker.example.com"))
    }
}
