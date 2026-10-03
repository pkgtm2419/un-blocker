package com.unblocker.app.quality

import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.rules.CompiledRuleSet
import com.unblocker.app.logic.rules.RuleAction
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeverBlockTest {
    @Test
    fun testCompiledRulesNeverBlockProtectedDomains() {
        val tsvFile = File("build/generated/dns-assets/dns-rules.tsv")
        assertTrue("dns-rules.tsv must exist", tsvFile.exists())
        val rules = tsvFile.reader().use { CompiledRuleSet.fromTsv(it) }
        val detector = AdDetector(compiledRules = rules)

        val neverBlockFile = File("src/test/resources/never-block.tsv")
        assertTrue("never-block.tsv must exist", neverBlockFile.exists())
        val protectedHosts = neverBlockFile.readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertTrue("Protected hosts list must not be empty", protectedHosts.size >= 25)

        for (host in protectedHosts) {
            val (isBlocked, reason) = detector.isAdDomain(host)
            assertTrue("Never-block domain $host was blocked: $reason", !isBlocked)
            val matchedRule = detector.matchRule(host)
            if (matchedRule != null) {
                assertNotEquals("Never-block domain $host matched a BLOCK rule", RuleAction.BLOCK, matchedRule.action)
            }
        }
    }
}
