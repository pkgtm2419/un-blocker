package com.unblocker.app.analysis

import com.unblocker.app.logic.analysis.PublicSuffixRules
import org.junit.Assert.*
import org.junit.Test

class PublicSuffixRulesTest {
    private val psl = PublicSuffixRules("com\nco.uk\ncom.au\nblogspot.com\n*.ck\n!www.ck\n".lineSequence())
    @Test fun longestPublicAndPrivateBoundaryIsUsed() {
        assertEquals("shop.co.uk", psl.registrableDomain("metrics.shop.co.uk"))
        assertEquals("site.com.au", psl.registrableDomain("cdn.site.com.au"))
        assertEquals("tenant.blogspot.com", psl.registrableDomain("a.tenant.blogspot.com"))
        assertEquals("metrics", psl.subdomain("metrics.shop.co.uk"))
        assertNull(psl.registrableDomain("co.uk"))
    }
    @Test fun wildcardAndExceptionUseWholeLabels() {
        assertEquals("a.b.ck", psl.registrableDomain("a.b.ck"))
        assertEquals("www.ck", psl.registrableDomain("a.www.ck"))
        assertEquals("a", psl.subdomain("a.www.ck"))
        assertEquals("site.test", psl.registrableDomain("a.site.test"))
    }
    @Test fun newerUnicodeSuffixDoesNotCrashOfflineLoading() {
        val unicode = PublicSuffixRules("com\nᬩᬮᬶ".lineSequence())
        assertNotNull(unicode.registrableDomain("example.xn--9tfky"))
    }
}
