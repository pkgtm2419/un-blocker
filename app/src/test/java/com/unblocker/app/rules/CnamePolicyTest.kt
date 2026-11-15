package com.unblocker.app.rules

import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.rules.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression coverage for cname policy.
 */
class CnamePolicyTest {
    private val engine = DnsRuleEngine(CompiledRuleSet(listOf(RuleParser.parse("||tracker.test^")!!)))

    /**
     * Verifies that original allow and current switch override aliases.
     */
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

    /**
     * Verifies that benign and shipped exceptions are not alias blocks.
     */
    @Test fun benignAndShippedExceptionsAreNotAliasBlocks() {
        val matcher =
            DnsRuleEngine(
                CompiledRuleSet(
                    listOf(
                        RuleParser.parse("||tracker.test^")!!,
                        RuleParser.parse("@@||good.tracker.test^")!!,
                    ),
                ),
            )
        assertNull(
            CnamePolicy({ true }, { false }, matcher::match).blockedAlias(
                "shop.test",
                listOf("cdn.test", "good.tracker.test"),
            ),
        )
    }

    /**
     * Verifies that protected original domain is not blocked by alias.
     */
    @Test fun protectedOriginalDomainIsNotBlockedByAlias() {
        val protectedDomains = DefaultProtectedDomains(builtIn = setOf("google.com", "docs.google.com"))
        val matcher = DnsRuleEngine(CompiledRuleSet(listOf(RuleParser.parse("||tracker.test^")!!)))
        val policy = CnamePolicy({ true }, { false }, matcher::match, protectedDomains)
        assertNull(policy.blockedAlias("docs.google.com", listOf("x.tracker.test")))
    }
}
