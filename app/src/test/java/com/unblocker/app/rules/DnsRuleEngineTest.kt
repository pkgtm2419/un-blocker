package com.unblocker.app.rules

import com.unblocker.app.logic.rules.*
import org.junit.Assert.*
import org.junit.Test

class DnsRuleEngineTest {
    private fun engine(vararg lines: String) = DnsRuleEngine(
        CompiledRuleSet(lines.map { RuleParser.parse(it)!! })
    )

    @Test fun exactRuleDoesNotMatchParentOrSubdomains() {
        val rules = engine("ads.example.com")
        assertEquals(RuleAction.BLOCK, rules.match("ads.example.com")?.action)
        assertNull(rules.match("sub.ads.example.com"))
        assertNull(rules.match("notads.example.com"))
    }

    @Test fun suffixMatchesOnlyWholeLabelBoundaries() {
        val rules = engine("||tracker.test^")
        assertNotNull(rules.match("tracker.test"))
        assertNotNull(rules.match("a.b.tracker.test"))
        assertNull(rules.match("nottracker.test"))
        assertNull(rules.match("tracker.test.evil.test"))
    }

    @Test fun explicitExceptionWinsRegardlessOfInputOrdering() {
        for (lines in listOf(arrayOf("||example.test^", "@@||good.example.test^"),
            arrayOf("@@||good.example.test^", "||example.test^"))) {
            val rules = engine(*lines)
            assertEquals(RuleAction.ALLOW, rules.match("sub.good.example.test")?.action)
            assertEquals(RuleAction.BLOCK, rules.match("bad.example.test")?.action)
        }
    }

    @Test fun wildcardRequiresASubdomain() {
        val rules = engine("*.ads.example.test")
        assertNull(rules.match("ads.example.test"))
        assertNotNull(rules.match("a.ads.example.test"))
        assertNotNull(rules.match("a.b.ads.example.test"))
    }

    @Test fun malformedRulesAreRejectedRatherThanBroadened() {
        listOf("https://example.test/path", "||example.test^$" + "important", "*example.test",
            "@@example.test", "bad..test", "例子.test", "||^", "# comment", "! comment").forEach {
            assertNull(it, RuleParser.parse(it))
        }
        assertEquals("example.test", RuleParser.parse("||EXAMPLE.TEST.^")!!.value)
    }

    @Test fun genericLexicalConceptIsNotDeterministicAuthority() {
        val detector = com.unblocker.app.logic.AdDetector()
        listOf("metrics.school.test", "analytics.library.test", "affiliate.books.test",
            "sponsor.community.test", "ads.randomwebsite.com").forEach {
            assertFalse(it, detector.isAdDomain(it).first)
        }
        assertTrue(detector.isAdDomain("sub.doubleclick.net").first)
    }
}
