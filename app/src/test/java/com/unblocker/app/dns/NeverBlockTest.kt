package com.unblocker.app.dns

import com.unblocker.app.logic.analysis.NeverBlockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeverBlockTest {

    @Test
    fun testNeverBlockConsistency() {
        val rules = setOf("dns.google", "cloudflare-dns.com", "one.one.one.one", "connectivitycheck.gstatic.com", "captiveportal.api.amazon.com", "a.applovin.com")
        NeverBlockPolicy.injectForTest(rules)

        assertTrue(NeverBlockPolicy.isNeverBlock("dns.google"))
        assertTrue(NeverBlockPolicy.isNeverBlock("cloudflare-dns.com"))
        assertTrue(NeverBlockPolicy.isNeverBlock("connectivitycheck.gstatic.com"))
        assertTrue(NeverBlockPolicy.isNeverBlock("A.APPLOVIN.COM"))
        
        assertFalse(NeverBlockPolicy.isNeverBlock("malicious.com"))
        assertFalse(NeverBlockPolicy.isNeverBlock("applovin.com"))
    }
}
