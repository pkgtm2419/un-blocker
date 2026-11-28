package com.unblocker.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DeepLinkParser] validating allowed internal routes, `unblocker://start`
 * non-auto-start semantics, and rejection of malformed, oversized, or injection payloads (WP-11 / F26).
 */
class DeepLinkParserTest {
    @Test
    fun parsesAllSupportedRoutesCleanly() {
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Home, autoStartVpn = false),
            DeepLinkParser.parse("unblocker://home"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Learn),
            DeepLinkParser.parse("unblocker://learn"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Logs(domain = null)),
            DeepLinkParser.parse("unblocker://logs"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Logs(domain = "ads.example.com")),
            DeepLinkParser.parse("unblocker://logs?domain=ADS.EXAMPLE.COM."),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Settings(section = null)),
            DeepLinkParser.parse("unblocker://settings"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Settings(section = "privacy")),
            DeepLinkParser.parse("unblocker://settings/privacy"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Exceptions),
            DeepLinkParser.parse("unblocker://exceptions"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Statistics),
            DeepLinkParser.parse("unblocker://stats"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Extensions),
            DeepLinkParser.parse("unblocker://extensions"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.Onboarding),
            DeepLinkParser.parse("unblocker://onboarding"),
        )
        assertEquals(
            DeepLinkResult.Matched(AppRoute.About),
            DeepLinkParser.parse("unblocker://about"),
        )
    }

    @Test
    fun startDeepLinkOpensHomeAndNeverAutoStartsVpn() {
        val result = DeepLinkParser.parse("unblocker://start")
        assertTrue(result is DeepLinkResult.Matched)
        val matched = result as DeepLinkResult.Matched
        assertEquals(AppRoute.Home, matched.route)
        assertFalse("unblocker://start must NEVER silently start VPN", matched.autoStartVpn)
    }

    @Test
    fun rejectsMalformedSchemesUnknownHostsAndOversizedUris() {
        assertTrue(DeepLinkParser.parse(null) is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("https://example.com") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("javascript:alert(1)") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("file:///etc/passwd") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://unknown_screen") is DeepLinkResult.Invalid)

        val oversized = "unblocker://logs?domain=" + "a".repeat(600) + ".com"
        assertTrue(DeepLinkParser.parse(oversized) is DeepLinkResult.Invalid)
    }

    @Test
    fun rejectsInjectionControlCharsAndTraversalSequences() {
        assertTrue(DeepLinkParser.parse("unblocker://logs?domain=<script>alert(1)</script>") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://logs?domain=' OR 1=1--") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://logs?other=example.com") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://settings/../../etc/passwd") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://settings/nonexistent_section") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://home\r\ninjected") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://logs?domain=test%00.com") is DeepLinkResult.Invalid)
        assertTrue(DeepLinkParser.parse("unblocker://user@home:8080#frag") is DeepLinkResult.Invalid)
    }
}
