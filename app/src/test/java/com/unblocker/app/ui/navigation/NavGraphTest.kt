package com.unblocker.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Navigation Compose destination patterns, uniqueness, argument formatting,
 * and bottom-bar tab visibility mapping (WP-11 / F26).
 */
class NavGraphTest {
    @Test
    fun allDestinationPatternsAreUniqueAndNonEmpty() {
        val patterns = UnblockerDestinations.allPatterns
        assertEquals(9, patterns.size)
        assertEquals(patterns.toSet().size, patterns.size)
        assertTrue(patterns.all { it.isNotBlank() })
    }

    @Test
    fun topLevelTabsMatchFourExpectedDestinations() {
        val tabs = UnblockerDestinations.topLevelTabs
        assertEquals(4, tabs.size)
        assertEquals(AppRoute.Home, tabs[0])
        assertEquals(AppRoute.Learn, tabs[1])
        assertTrue(tabs[2] is AppRoute.Logs)
        assertTrue(tabs[3] is AppRoute.Settings)

        assertEquals(0, UnblockerDestinations.selectedTabIndex(AppRoute.Home.pattern))
        assertEquals(1, UnblockerDestinations.selectedTabIndex(AppRoute.Learn.pattern))
        assertEquals(2, UnblockerDestinations.selectedTabIndex(AppRoute.Logs.PATTERN))
        assertEquals(3, UnblockerDestinations.selectedTabIndex(AppRoute.Settings.PATTERN))

        assertTrue(UnblockerDestinations.isTopLevelPattern(AppRoute.Home.pattern))
        assertTrue(UnblockerDestinations.isTopLevelPattern(AppRoute.Learn.pattern))
        assertTrue(UnblockerDestinations.isTopLevelPattern(AppRoute.Logs.PATTERN))
        assertTrue(UnblockerDestinations.isTopLevelPattern(AppRoute.Settings.PATTERN))
    }

    @Test
    fun secondaryDestinationsHideBottomBarAndMapToMinusOneTab() {
        val secondary =
            listOf(
                AppRoute.Extensions.pattern,
                AppRoute.Exceptions.pattern,
                AppRoute.Statistics.pattern,
                AppRoute.Onboarding.pattern,
                AppRoute.About.pattern,
            )
        secondary.forEach { route ->
            assertEquals(-1, UnblockerDestinations.selectedTabIndex(route))
            assertFalse(UnblockerDestinations.isTopLevelPattern(route))
        }
    }

    @Test
    fun routeBuildersEncodeArgumentsCleanly() {
        assertEquals("logs", AppRoute.Logs().toNavRoute())
        assertEquals("logs?domain=ads.example.com", AppRoute.Logs("ads.example.com").toNavRoute())
        assertEquals("settings", AppRoute.Settings().toNavRoute())
        assertEquals("settings?section=privacy", AppRoute.Settings("privacy").toNavRoute())
    }
}
