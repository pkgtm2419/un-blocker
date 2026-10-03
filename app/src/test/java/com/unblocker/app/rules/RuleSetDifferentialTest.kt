package com.unblocker.app.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.rules.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

class RuleSetDifferentialTest {

    @Test
    fun differentialMatchesOracleOnSyntheticEdgeCases() {
        val rules = listOf(
            DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, "doubleclick.net", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.EXACT, "ad.doubleclick.net", BlockingCategory.AD, 10),
            DnsRule(RuleAction.BLOCK, RuleKind.WILDCARD, "wildcard.example.com", BlockingCategory.AD, 1),
            DnsRule(RuleAction.BLOCK, RuleKind.EXACT, "exact.example.com", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, "allowed-suffix.org", BlockingCategory.AD, 10),
            DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, "blocked-parent.org", BlockingCategory.AD, 1),
            DnsRule(RuleAction.ALLOW, RuleKind.EXACT, "sub.blocked-parent.org", BlockingCategory.AD, 10)
        )

        val compiled = CompiledRuleSet(rules)
        val oracle = DnsRuleEngine(compiled)
        val ruleSet = compiled.toRuleSet()

        val queries = listOf(
            "doubleclick.net",
            "ad.doubleclick.net",
            "sub.ad.doubleclick.net",
            "other.doubleclick.net",
            "wildcard.example.com",
            "sub.wildcard.example.com",
            "sub.sub.wildcard.example.com",
            "exact.example.com",
            "sub.exact.example.com",
            "allowed-suffix.org",
            "sub.allowed-suffix.org",
            "blocked-parent.org",
            "sub.blocked-parent.org",
            "deep.sub.blocked-parent.org",
            "unrelated.com",
            "example.org",
            "DOUBLECLICK.NET.",
            "AD.DOUBLECLICK.NET."
        )

        for (q in queries) {
            val oracleMatch = oracle.match(q)
            val ruleSetMatch = ruleSet.match(q)
            assertEquals("Mismatch for query: $q", oracleMatch, ruleSetMatch)
        }
    }

    @Test
    fun differentialMatchesOracleOnRandomRuleCorpus() {
        val rnd = Random(42)
        val generatedRules = mutableListOf<DnsRule>()

        // Generate 1,500 diverse rules
        for (i in 0 until 1_500) {
            val domain = "host-$i.domain-${i % 50}.test"
            val action = if (rnd.nextInt(10) == 0) RuleAction.ALLOW else RuleAction.BLOCK
            val kind = when (rnd.nextInt(3)) {
                0 -> RuleKind.EXACT
                1 -> RuleKind.SUFFIX
                else -> RuleKind.WILDCARD
            }
            generatedRules.add(DnsRule(action, kind, domain, BlockingCategory.AD, i % 5))
        }

        val compiled = CompiledRuleSet(generatedRules)
        val oracle = DnsRuleEngine(compiled)
        val ruleSet = compiled.toRuleSet()

        val queries = mutableListOf<String>()
        // Direct hits
        for (r in generatedRules.take(300)) {
            queries.add(r.value)
            queries.add("sub." + r.value)
            queries.add("deep.sub." + r.value)
        }
        // Misses and unrelated
        for (i in 0 until 500) {
            queries.add("unrelated-$i.other-${i % 20}.com")
            queries.add("domain-${i % 50}.test")
        }

        var matchesFound = 0
        for (q in queries) {
            val oracleMatch = oracle.match(q)
            val ruleSetMatch = ruleSet.match(q)
            assertEquals("Mismatch for query '$q'", oracleMatch, ruleSetMatch)
            if (ruleSetMatch != null) matchesFound++
        }

        assert(matchesFound > 0) { "Expected positive matches during differential test" }
    }
}
