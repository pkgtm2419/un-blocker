package com.unblocker.app.rules

import com.unblocker.app.logic.rules.*
import org.junit.Assert.*
import org.junit.Test

class PackedMatcherTest {
    @Test fun equivalentAcrossSeededHitsMissesAndBoundaryCases() {
        val lines=(0..999).map { "||host$it.test^" } + listOf("@@||good.host1.test^", "exact.test", "*.wild.test")
        val rules=CompiledRuleSet(lines.map { RuleParser.parse(it)!! })
        val baseline=DnsRuleEngine(rules)
        val packed=rules.toRuleSet()
        (lines.mapNotNull { RuleParser.parse(it)?.value } + (0..999).flatMap {
            listOf("a.host$it.test", "nothost$it.test", "host$it.test.evil") } +
            listOf("GOOD.HOST1.TEST.", "a.good.host1.test", "a.wild.test", "wild.test", "a.exact.test")).forEach {
            assertEquals(it,baseline.match(it),packed.match(it))
        }
    }
}
