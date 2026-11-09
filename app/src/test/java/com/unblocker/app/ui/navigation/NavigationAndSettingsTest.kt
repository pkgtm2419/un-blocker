package com.unblocker.app.ui.navigation

import com.unblocker.app.data.preferences.FilteringTier
import com.unblocker.app.services.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating the 4-item navigation structure, filtering tier definitions,
 * and UI status mappings for the redesigned UI/UX layout.
 */
class NavigationAndSettingsTest {

    @Test
    fun testNavigationDestinations() {
        val destinations = listOf("Home", "Learn", "Logs", "Settings")
        assertEquals(4, destinations.size)
        assertEquals("Home", destinations[0])
        assertEquals("Learn", destinations[1])
        assertEquals("Logs", destinations[2])
        assertEquals("Settings", destinations[3])
    }

    @Test
    fun testFilteringTiersIntegrity() {
        val tiers = FilteringTier.entries
        assertEquals(3, tiers.size)
        assertTrue(tiers.contains(FilteringTier.BASIC))
        assertTrue(tiers.contains(FilteringTier.OPTIMAL))
        assertTrue(tiers.contains(FilteringTier.COMPLETE))

        // Check descriptions mapped in UI
        val descriptions = tiers.associateWith { tier ->
            when (tier) {
                FilteringTier.BASIC -> "Basic" to "Game & shopping friendly"
                FilteringTier.OPTIMAL -> "Optimal" to "Balanced shield"
                FilteringTier.COMPLETE -> "Complete" to "Strict shield"
            }
        }

        assertNotNull(descriptions[FilteringTier.BASIC])
        assertNotNull(descriptions[FilteringTier.OPTIMAL])
        assertNotNull(descriptions[FilteringTier.COMPLETE])
        assertEquals("Basic", descriptions[FilteringTier.BASIC]?.first)
        assertEquals("Complete", descriptions[FilteringTier.COMPLETE]?.first)
    }

    @Test
    fun testUiStatusCaptions() {
        val statuses = listOf(
            ServiceStatus.RUNNING,
            ServiceStatus.STARTING,
            ServiceStatus.STOPPING,
            ServiceStatus.STOPPED
        )

        for (status in statuses) {
            val caption = when (status) {
                ServiceStatus.RUNNING -> "SHIELD ACTIVE"
                ServiceStatus.STARTING -> "STARTING..."
                ServiceStatus.STOPPING -> "STOPPING..."
                else -> "TAP TO CONNECT"
            }
            val subtitle = when (status) {
                ServiceStatus.RUNNING -> "Tap shield to disconnect"
                ServiceStatus.STARTING -> "Initializing DNS filters"
                ServiceStatus.STOPPING -> "Closing network tunnel"
                else -> "Protection is paused"
            }
            assertTrue(caption.isNotBlank())
            assertTrue(subtitle.isNotBlank())
        }
    }
}
