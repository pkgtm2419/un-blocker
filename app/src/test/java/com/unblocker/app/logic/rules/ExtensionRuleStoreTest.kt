package com.unblocker.app.logic.rules

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExtensionRuleStoreTest {

    @Before
    @After
    fun cleanUp() {
        ExtensionRuleStore.clearForTesting()
    }

    @Test
    fun testParseLine() {
        val suffixRule = ExtensionRuleStore.parseLine("||doubleclick.net^")
        assertNotNull(suffixRule)
        assertEquals(RuleAction.BLOCK, suffixRule!!.action)
        assertEquals(RuleKind.SUFFIX, suffixRule.kind)
        assertEquals("doubleclick.net", suffixRule.value)

        val hostsRule = ExtensionRuleStore.parseLine("0.0.0.0 tracker.com")
        assertNotNull(hostsRule)
        assertEquals(RuleAction.BLOCK, hostsRule!!.action)
        assertEquals(RuleKind.EXACT, hostsRule.kind)
        assertEquals("tracker.com", hostsRule.value)

        val loopbackRule = ExtensionRuleStore.parseLine("127.0.0.1 analytics.io")
        assertNotNull(loopbackRule)
        assertEquals("analytics.io", loopbackRule!!.value)

        val allowRule = ExtensionRuleStore.parseLine("@@||goodservice.com^")
        assertNotNull(allowRule)
        assertEquals(RuleAction.ALLOW, allowRule!!.action)
        assertEquals(RuleKind.SUFFIX, allowRule.kind)
        assertEquals("goodservice.com", allowRule.value)

        val optionsRule = ExtensionRuleStore.parseLine("||adserver.net^\$third-party,image")
        assertNotNull(optionsRule)
        assertEquals("adserver.net", optionsRule!!.value)

        val commentRule = ExtensionRuleStore.parseLine("! This is a comment")
        assertNull(commentRule)

        val emptyRule = ExtensionRuleStore.parseLine("   ")
        assertNull(emptyRule)
    }

    @Test
    fun testRuleMatching() {
        val r1 = ExtensionRuleStore.parseLine("||badads.com^")!!
        val r2 = ExtensionRuleStore.parseLine("exacttracker.net")!!
        val r3 = ExtensionRuleStore.parseLine("@@||safe.badads.com^")!!
        val r4 = ExtensionRuleStore.parseLine("*.subdomainonly.com")!!

        ExtensionRuleStore.setRules(listOf(r1, r2, r3, r4))

        // Exact match
        val mExact = ExtensionRuleStore.matchRule("exacttracker.net")
        assertNotNull(mExact)
        assertEquals(RuleAction.BLOCK, mExact!!.action)

        // Subdomain of exact should NOT match exact
        val mSubExact = ExtensionRuleStore.matchRule("sub.exacttracker.net")
        assertNull(mSubExact)

        // Suffix match
        val mSuffix = ExtensionRuleStore.matchRule("ad1.badads.com")
        assertNotNull(mSuffix)
        assertEquals(RuleAction.BLOCK, mSuffix!!.action)
        assertEquals("badads.com", mSuffix.value)

        // Allow rule overrides suffix
        val mAllow = ExtensionRuleStore.matchRule("safe.badads.com")
        assertNotNull(mAllow)
        assertEquals(RuleAction.ALLOW, mAllow!!.action)

        val mSubAllow = ExtensionRuleStore.matchRule("api.safe.badads.com")
        assertNotNull(mSubAllow)
        assertEquals(RuleAction.ALLOW, mSubAllow!!.action)

        // Wildcard match
        val mWildcard = ExtensionRuleStore.matchRule("test.subdomainonly.com")
        assertNotNull(mWildcard)
        assertEquals(RuleAction.BLOCK, mWildcard!!.action)

        // Unrelated domain
        val mClean = ExtensionRuleStore.matchRule("wikipedia.org")
        assertNull(mClean)
    }

    @Test
    fun testBuilderStreamingDirectIngestion() {
        val builder = ExtensionRuleStore.Builder()
        builder.addLine("! Header comment")
        builder.addLine("||samsungads.com^")
        builder.addLine("0.0.0.0 tracking.miui.com")
        builder.addLine("@@||allowme.samsungads.com^")
        builder.addLine("   ")

        assertEquals(3, builder.count)

        ExtensionRuleStore.applyBuilder(builder)
        assertEquals(3, ExtensionRuleStore.ruleCount)

        val blocked1 = ExtensionRuleStore.matchRule("samsungads.com")
        assertNotNull(blocked1)
        assertEquals(RuleAction.BLOCK, blocked1!!.action)

        val blocked2 = ExtensionRuleStore.matchRule("sub.samsungads.com")
        assertNotNull(blocked2)
        assertEquals(RuleAction.BLOCK, blocked2!!.action)

        val allowed = ExtensionRuleStore.matchRule("allowme.samsungads.com")
        assertNotNull(allowed)
        assertEquals(RuleAction.ALLOW, allowed!!.action)

        val blockedHost = ExtensionRuleStore.matchRule("tracking.miui.com")
        assertNotNull(blockedHost)
        assertEquals(RuleAction.BLOCK, blockedHost!!.action)
    }
}
