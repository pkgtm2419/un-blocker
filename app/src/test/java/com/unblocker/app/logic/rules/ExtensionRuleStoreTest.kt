package com.unblocker.app.logic.rules

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Regression coverage for extension rule store.
 */
class ExtensionRuleStoreTest {
    /**
     * Releases test resources and restores shared state after each test.
     */
    @Before
    @After
    fun cleanUp() {
        ExtensionRuleStore.clearForTesting()
    }

    /**
     * Verifies that test parse line.
     */
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

        val hostsWithComment = ExtensionRuleStore.parseLine("0.0.0.0 tracker.com # inline comment")
        assertNotNull(hostsWithComment)
        assertEquals("tracker.com", hostsWithComment!!.value)

        val loopbackRule = ExtensionRuleStore.parseLine("127.0.0.1 analytics.io # telemetry")
        assertNotNull(loopbackRule)
        assertEquals("analytics.io", loopbackRule!!.value)

        val localhostRule = ExtensionRuleStore.parseLine("0.0.0.0 localhost")
        assertNull(localhostRule)

        val ipv6Loopback = ExtensionRuleStore.parseLine("::1 localhost")
        assertNull(ipv6Loopback)

        val allowRule = ExtensionRuleStore.parseLine("@@||goodservice.com^")
        assertNotNull(allowRule)
        assertEquals(RuleAction.ALLOW, allowRule!!.action)
        assertEquals(RuleKind.SUFFIX, allowRule.kind)
        assertEquals("goodservice.com", allowRule.value)

        val bareOptionsRule = ExtensionRuleStore.parseLine("||adserver.net^\$third-party,image")
        assertNull(bareOptionsRule)

        // A request-scoped filter must never become a domain-wide DNS block.
        val subOptionsRule = ExtensionRuleStore.parseLine("||ads.adserver.net^\$third-party,image")
        assertNull(subOptionsRule)

        val commentRule = ExtensionRuleStore.parseLine("! This is a comment")
        assertNull(commentRule)

        val emptyRule = ExtensionRuleStore.parseLine("   ")
        assertNull(emptyRule)
    }

    /**
     * Verifies that test rule matching.
     */
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

    /**
     * Verifies that wildcard allow rules only unblock strict subdomains, preserving the apex block.
     */
    @Test
    fun testWildcardAllowDoesNotAllowBaseHost() {
        val blockBase = ExtensionRuleStore.parseLine("||adserver.example^")!!
        val allowChildren = ExtensionRuleStore.parseLine("@@*.adserver.example")!!
        ExtensionRuleStore.setRules(listOf(blockBase, allowChildren))

        assertEquals(RuleAction.BLOCK, ExtensionRuleStore.matchRule("adserver.example")?.action)
        assertEquals(RuleAction.ALLOW, ExtensionRuleStore.matchRule("a.adserver.example")?.action)
        assertEquals(RuleKind.WILDCARD, ExtensionRuleStore.matchRule("a.adserver.example")?.kind)
        assertEquals(RuleAction.ALLOW, ExtensionRuleStore.matchRule("b.a.adserver.example")?.action)
    }

    /**
     * Verifies that test single label parent never blocks.
     */
    @Test
    fun testSingleLabelParentNeverBlocks() {
        // Even if an admitted rule with value 'com' exists in blockSuffix
        val builder = ExtensionRuleStore.Builder()
        builder.blockSuffix.add("com")
        builder.count = 1
        ExtensionRuleStore.applyBuilder(builder)

        assertNull(ExtensionRuleStore.matchRule("example.com"))
        assertNull(ExtensionRuleStore.matchRule("google.com"))
    }

    /**
     * Verifies that test builder streaming direct ingestion.
     */
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

    /**
     * Verifies that test builder deduplicates rule count.
     */
    @Test
    fun testBuilderDeduplicatesRuleCount() {
        val builder = ExtensionRuleStore.Builder()
        builder.addLine("||samsungads.com^")
        builder.addLine("||samsungads.com^")
        builder.addLine("0.0.0.0 tracking.miui.com")
        builder.addLine("0.0.0.0 tracking.miui.com # comment")
        assertEquals(2, builder.count)

        ExtensionRuleStore.applyBuilder(builder)
        assertEquals(2, ExtensionRuleStore.ruleCount)
    }

    /**
     * Verifies that bundled rules asset ingests correctly and blocks new mobile ads and cryptomining domains.
     */
    @Test
    fun testBundledRulesAssetIngestionAndBlocking() {
        val assetFile = File("src/main/assets/extensions/bundled_rules.txt")
        assertTrue("bundled_rules.txt must exist in assets", assetFile.exists())

        val builder = ExtensionRuleStore.Builder()
        assetFile.forEachLine { line ->
            builder.addLine(line)
        }

        assertTrue("Expected at least 450 rules in bundled set", builder.count >= 450)
        ExtensionRuleStore.applyBuilder(builder)

        val representativeAdDomains =
            listOf(
                "coinhive.com",
                "crypto-loot.com",
                "ads.chartboost.com",
                "pangle-sdk.com",
                "mediation.fyber.com",
                "events.vungle.com",
                "attr.appsflyer.com",
                "log.ironsrc.com",
                "admin.appnext.com",
                "sspapi-prd.samsungrs.com",
                "ketchapp.org",
            )

        for (domain in representativeAdDomains) {
            val matched = ExtensionRuleStore.matchRule(domain)
            assertNotNull("Domain $domain should be blocked by bundled rules", matched)
            assertEquals("Expected BLOCK action for $domain", RuleAction.BLOCK, matched!!.action)
        }

        assertNull(
            "Legitimate domain should not be blocked",
            ExtensionRuleStore.matchRule("wikipedia.org"),
        )
    }
}
