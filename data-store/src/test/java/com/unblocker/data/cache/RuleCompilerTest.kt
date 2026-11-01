package com.unblocker.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * Unit tests for [RuleCompiler] and [OffHeapRuleEngine].
 *
 * Verifies multi-syntax parsing, 64-bit FNV-1a indexing, DoH bootstrap pre-seeding,
 * and zero-allocation off-heap lookups.
 */
class RuleCompilerTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun testExtractCleanDomain_multiSyntax() {
        // Adblock / uBlock syntax
        assertEquals(
            "googleads.g.doubleclick.net",
            RuleCompiler.extractCleanDomain("||googleads.g.doubleclick.net^\$third-party")
        )
        assertEquals(
            "adservice.google.com",
            RuleCompiler.extractCleanDomain("||adservice.google.com^")
        )

        // Hosts syntax
        assertEquals(
            "pagead2.googlesyndication.com",
            RuleCompiler.extractCleanDomain("0.0.0.0 pagead2.googlesyndication.com")
        )
        assertEquals(
            "tracker.example.org",
            RuleCompiler.extractCleanDomain("127.0.0.1 tracker.example.org # inline comment")
        )
        assertEquals(
            "ipv6tracker.com",
            RuleCompiler.extractCleanDomain("::1 ipv6tracker.com")
        )

        // Plain domain syntax
        assertEquals(
            "analytics.yahoo.com",
            RuleCompiler.extractCleanDomain("analytics.yahoo.com")
        )

        // Invalid, comments, cosmetic rules, and exceptions
        assertNull(RuleCompiler.extractCleanDomain("# This is a comment"))
        assertNull(RuleCompiler.extractCleanDomain("! Another comment"))
        assertNull(RuleCompiler.extractCleanDomain("[Adblock Plus 2.0]"))
        assertNull(RuleCompiler.extractCleanDomain("@@||whitelist.com^"))
        assertNull(RuleCompiler.extractCleanDomain("##.ad-banner"))
        assertNull(RuleCompiler.extractCleanDomain("example.com##.ad-box"))
        assertNull(RuleCompiler.extractCleanDomain("example.com#@#.ad-box"))
        assertNull(RuleCompiler.extractCleanDomain("localhost"))
        assertNull(RuleCompiler.extractCleanDomain("invalid_domain_without_dot"))
        assertNull(RuleCompiler.extractCleanDomain("0.0.0.0 localhost"))
        assertNull(RuleCompiler.extractCleanDomain("   "))
    }

    @Test
    fun testFnv1a64_caseInsensitiveAndSubstringEquivalence() {
        val uppercaseDomain = "GoogleAds.G.DoubleClick.Net"
        val lowercaseDomain = "googleads.g.doubleclick.net"

        val hash1 = RuleCompiler.fnv1a64(uppercaseDomain)
        val hash2 = RuleCompiler.fnv1a64(lowercaseDomain)
        assertEquals("FNV-1a must be case-insensitive", hash1, hash2)

        val fullText = "prefix.googleads.g.doubleclick.net.suffix"
        val start = 7
        val end = 7 + lowercaseDomain.length
        val substringHash = RuleCompiler.fnv1a64(fullText, start, end)
        assertEquals("Sub-range hash must equal direct string hash", hash2, substringHash)
    }

    @Test
    fun testCompileStreamsAndOffHeapEngineVerification() {
        val rulesContent = """
            # Adblock syntax rule
            ||googleads.g.doubleclick.net^${'$'}third-party
            # Hosts syntax rule
            0.0.0.0 pagead2.googlesyndication.com
            # Extra test rule
            ||evil-tracker.com^
        """.trimIndent()

        val reader = BufferedReader(StringReader(rulesContent))
        val outputFile = File(tempFolder.root, "rules.bin")

        val count = RuleCompiler.compileStreamsToBinaryFile(listOf(reader), outputFile)
        assertTrue("Output file must exist", outputFile.exists())
        assertTrue("Compiled rules count must include bootstrap domains", count >= 3 + RuleCompiler.DOH_BOOTSTRAP_DOMAINS.size)

        val engine = OffHeapRuleEngine()
        engine.reloadFromFile(outputFile)
        assertTrue("Engine must be loaded", engine.isLoaded())
        assertEquals("Engine rule count must match compiled count", count, engine.ruleCount())

        // 1. Verify ||googleads.g.doubleclick.net^$third-party is blocked
        assertTrue(
            "googleads.g.doubleclick.net must be blocked",
            engine.isDomainBlocked("googleads.g.doubleclick.net")
        )

        // 2. Verify 0.0.0.0 pagead2.googlesyndication.com is blocked
        assertTrue(
            "pagead2.googlesyndication.com must be blocked",
            engine.isDomainBlocked("pagead2.googlesyndication.com")
        )

        // 3. Verify subdomain sub.pagead2.googlesyndication.com is blocked via parent walk
        assertTrue(
            "sub.pagead2.googlesyndication.com must be blocked",
            engine.isDomainBlocked("sub.pagead2.googlesyndication.com")
        )
        assertTrue(
            "deep.nested.sub.pagead2.googlesyndication.com must be blocked",
            engine.isDomainBlocked("deep.nested.sub.pagead2.googlesyndication.com")
        )

        // 4. Verify DoH bootstrap domains are blocked
        for (doh in RuleCompiler.DOH_BOOTSTRAP_DOMAINS) {
            assertTrue("DoH bootstrap domain $doh must be blocked", engine.isDomainBlocked(doh))
        }

        // 5. Verify legitimate domain is not blocked
        assertFalse(
            "google.com must not be blocked",
            engine.isDomainBlocked("google.com")
        )
        assertFalse(
            "example.org must not be blocked",
            engine.isDomainBlocked("example.org")
        )
    }
}
