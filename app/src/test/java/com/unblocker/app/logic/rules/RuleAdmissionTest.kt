package com.unblocker.app.logic.rules

import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.PublicSuffixRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleAdmissionTest {

    private val psl = PublicSuffixRules.fallback
    private val protectedHosts = setOf(
        "google.com",
        "www.google.com",
        "youtube.com",
        "www.youtube.com"
    )
    private val protected = DefaultProtectedDomains(builtIn = protectedHosts, psl = psl)
    private val admission = DefaultRuleAdmission(psl = psl, protected = protected)

    @Test
    fun testIpLiteralsRejected() {
        assertEquals(SkipReason.IP_LITERAL, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "192.168.1.1"))
        assertEquals(SkipReason.IP_LITERAL, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "10.0.0.1"))
        assertEquals(SkipReason.IP_LITERAL, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "::1"))
        assertEquals(SkipReason.IP_LITERAL, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "fe80::1"))
    }

    @Test
    fun testSingleLabelsRejected() {
        assertEquals(SkipReason.SINGLE_LABEL, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "com"))
        assertEquals(SkipReason.SINGLE_LABEL, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "net"))
        assertEquals(SkipReason.SINGLE_LABEL, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "localhost"))
    }

    @Test
    fun testPublicSuffixesRejected() {
        assertEquals(SkipReason.PUBLIC_SUFFIX, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "co.uk"))
        assertEquals(SkipReason.PUBLIC_SUFFIX, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "github.io"))
        assertEquals(SkipReason.PUBLIC_SUFFIX, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "blogspot.com"))
    }

    @Test
    fun testProtectedAncestorsRejected() {
        // ||google.com^ as a SUFFIX rule covers google.com and www.google.com
        assertEquals(SkipReason.PROTECTED_HOST, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "google.com"))
        assertEquals(SkipReason.PROTECTED_HOST, admission.check(RuleAction.BLOCK, RuleKind.EXACT, "google.com"))
        assertEquals(SkipReason.PROTECTED_HOST, admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "www.youtube.com"))

        // Subdomains that are not protected themselves should be admitted
        assertNull(admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "ads.google.com"))
        assertNull(admission.check(RuleAction.BLOCK, RuleKind.EXACT, "tracker.google.com"))
        assertNull(admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "adserver.net"))
    }

    @Test
    fun testQuarantinedRuleRejected() {
        QuarantineList.clearForTest()
        QuarantineList.addForTest(RuleKind.SUFFIX, "quarantined-tracker.com")

        assertEquals(
            SkipReason.QUARANTINED,
            admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, "quarantined-tracker.com")
        )

        QuarantineList.clearForTest()
    }
}
