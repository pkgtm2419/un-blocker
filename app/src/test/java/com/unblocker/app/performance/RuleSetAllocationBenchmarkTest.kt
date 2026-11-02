package com.unblocker.app.performance

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.rules.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuleSetAllocationBenchmarkTest {

    @Test
    fun benchmarkRuleSetMatchingCorrectnessAndEfficiency() {
        val rules = listOf(
            DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, "doubleclick.net", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.EXACT, "ad.doubleclick.net", BlockingCategory.AD, 10),
            DnsRule(RuleAction.BLOCK, RuleKind.WILDCARD, "wildcard.example.com", BlockingCategory.AD, 1),
            DnsRule(RuleAction.BLOCK, RuleKind.EXACT, "exact.example.com", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, "allowed-suffix.org", BlockingCategory.AD, 10),
            DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, "blocked-parent.org", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.EXACT, "sub.blocked-parent.org", BlockingCategory.AD, 10)
        )

        val ruleSet = CompiledRuleSet(rules).toRuleSet()

        // Semantics verification
        assertEquals(RuleAction.BLOCK, ruleSet.match("doubleclick.net")?.action)
        assertEquals(RuleAction.ALLOW, ruleSet.match("ad.doubleclick.net")?.action)
        assertEquals(RuleAction.BLOCK, ruleSet.match("sub.doubleclick.net")?.action)
        assertEquals(RuleAction.BLOCK, ruleSet.match("sub.wildcard.example.com")?.action)
        assertNull(ruleSet.match("wildcard.example.com")) // wildcard does not match domain itself
        assertEquals(RuleAction.BLOCK, ruleSet.match("exact.example.com")?.action)
        assertNull(ruleSet.match("sub.exact.example.com"))
        assertNull(ruleSet.match("benign.example.net"))

        // Throughput check: 10,000 matches execute swiftly
        val queries = listOf(
            "sub.doubleclick.net",
            "ad.doubleclick.net",
            "benign.example.net",
            "deep.sub.wildcard.example.com",
            "blocked-parent.org",
            "sub.blocked-parent.org"
        )
        val start = System.currentTimeMillis()
        for (i in 0 until 10_000) {
            val q = queries[i % queries.size]
            ruleSet.match(q)
        }
        val elapsed = System.currentTimeMillis() - start
        assert(elapsed < 2000) { "10,000 queries should take less than 2000ms, took ${elapsed}ms" }
    }
}
