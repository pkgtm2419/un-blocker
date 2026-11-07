package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.rules.RuleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedDomainsTest {

    private val psl = PublicSuffixRules.fallback
    private val builtIn = setOf(
        "google.com",
        "youtube.com",
        "docs.google.com"
    )
    private val myDomains = setOf("mybank.com", "myuniversity.edu")

    private val protectedDomains = DefaultProtectedDomains(
        builtIn = builtIn,
        myDomains = myDomains,
        psl = psl
    )

    @Test
    fun testBuiltInProtection() {
        assertTrue(protectedDomains.isProtected("google.com"))
        assertTrue(protectedDomains.isProtected("www.google.com"))
        assertTrue(protectedDomains.isProtected("docs.google.com"))
        assertTrue(protectedDomains.isProtected("youtube.com"))
        assertTrue(protectedDomains.isProtected("www.youtube.com"))

        // Unrelated domains
        assertFalse(protectedDomains.isProtected("ads.google.com"))
        assertFalse(protectedDomains.isProtected("example.com"))
    }

    @Test
    fun testMyDomainsProtection() {
        // Registrable domain
        assertTrue(protectedDomains.isProtected("mybank.com"))
        // Any subdomain of my domains
        assertTrue(protectedDomains.isProtected("login.mybank.com"))
        assertTrue(protectedDomains.isProtected("api.secure.mybank.com"))

        assertTrue(protectedDomains.isProtected("myuniversity.edu"))
        assertTrue(protectedDomains.isProtected("cs.myuniversity.edu"))

        assertFalse(protectedDomains.isProtected("otherbank.com"))
    }

    @Test
    fun testRuleWouldBlockProtected() {
        // SUFFIX rule on google.com blocks google.com, docs.google.com, www.google.com
        assertTrue(protectedDomains.ruleWouldBlockProtected(RuleKind.SUFFIX, "google.com"))
        assertTrue(protectedDomains.ruleWouldBlockProtected(RuleKind.EXACT, "google.com"))

        // SUFFIX rule on ads.google.com does NOT cover google.com or docs.google.com
        assertFalse(protectedDomains.ruleWouldBlockProtected(RuleKind.SUFFIX, "ads.google.com"))

        // SUFFIX rule on mybank.com covers mybank.com and all subdomains
        assertTrue(protectedDomains.ruleWouldBlockProtected(RuleKind.SUFFIX, "mybank.com"))
        assertTrue(protectedDomains.ruleWouldBlockProtected(RuleKind.SUFFIX, "login.mybank.com"))
    }
}
