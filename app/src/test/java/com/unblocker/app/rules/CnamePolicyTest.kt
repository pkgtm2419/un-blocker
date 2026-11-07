package com.unblocker.app.rules

import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.rules.*
import org.junit.Assert.*
import org.junit.Test

class CnamePolicyTest {
    private val engine = DnsRuleEngine(CompiledRuleSet(listOf(RuleParser.parse("||tracker.test^")!!)))

    @Test fun originalAllowAndCurrentSwitchOverrideAliases() {
        var enabled = true
        var allowed = false
        val policy = CnamePolicy({ enabled }, { allowed }, engine::match)
        assertEquals("x.tracker.test", policy.blockedAlias("shop.test", listOf("x.tracker.test")))
        allowed = true
        assertNull(policy.blockedAlias("shop.test", listOf("x.tracker.test")))
        allowed = false
        enabled = false
        assertNull(policy.blockedAlias("shop.test", listOf("x.tracker.test")))
    }

    @Test fun benignAndShippedExceptionsAreNotAliasBlocks() {
        val matcher = DnsRuleEngine(CompiledRuleSet(listOf(RuleParser.parse("||tracker.test^")!!,
            RuleParser.parse("@@||good.tracker.test^")!!)))
        assertNull(CnamePolicy({true}, {false}, matcher::match).blockedAlias("shop.test",
            listOf("cdn.test", "good.tracker.test")))
    }

    @Test fun protectedOriginalDomainIsNotBlockedByAlias() {
        val protectedDomains = DefaultProtectedDomains(builtIn = setOf("google.com", "docs.google.com"))
        val matcher = DnsRuleEngine(CompiledRuleSet(listOf(RuleParser.parse("||tracker.test^")!!)))
        val policy = CnamePolicy({ true }, { false }, matcher::match, protectedDomains)
        assertNull(policy.blockedAlias("docs.google.com", listOf("x.tracker.test")))
    }
}
