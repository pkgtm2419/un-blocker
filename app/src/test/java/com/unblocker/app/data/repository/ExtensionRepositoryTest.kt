package com.unblocker.app.data.repository

import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.data.model.FilterCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRepositoryTest {

    @Test
    fun testMigrateExtensionsPreservesUserCustomFiltersAndToggles() {
        // Previous stored extensions:
        // 1. User custom filter
        val custom = ExtensionFilter(
            id = "custom_123",
            name = "My Blocklist",
            url = "https://example.com/custom.txt",
            filterCount = 15,
            sizeBytes = 2048L,
            lastUpdatedEpochMs = 1728000000000L,
            isEnabled = true,
            isCustom = true,
            category = FilterCategories.CUSTOM
        )

        // 2. Existing extension that user turned OFF
        val easylistUserDisabled = ExtensionFilter(
            id = "easylist",
            name = "EasyList",
            url = "https://easylist.to/easylist/easylist.txt",
            filterCount = 38500,
            sizeBytes = 722944L,
            lastUpdatedEpochMs = 1728000000000L,
            isEnabled = false, // user disabled it!
            isCustom = false,
            category = FilterCategories.RECOMMENDED
        )

        // 3. Obsolete extension that was removed from new manifest
        val deadAak = ExtensionFilter(
            id = "aaklist",
            name = "AakList (Anti-Adblock Killer)",
            url = "https://example.com/dead.txt",
            filterCount = 1000,
            sizeBytes = 1000L,
            isEnabled = true,
            isCustom = false,
            category = FilterCategories.PRIVACY
        )

        val savedList = listOf(custom, easylistUserDisabled, deadAak)

        // New bundled manifest:
        val newEasylist = ExtensionFilter(
            id = "easylist",
            name = "EasyList",
            url = "https://easylist.to/easylist/easylist.txt",
            filterCount = 38500,
            sizeBytes = 722944L,
            lastUpdatedEpochMs = 1728412320000L,
            isEnabled = true, // default is enabled in manifest
            isCustom = false,
            category = FilterCategories.RECOMMENDED,
            description = "Primary ad-blocking filter list for global web traffic"
        )

        val newAdaway = ExtensionFilter(
            id = "adaway_default",
            name = "AdAway Mobile Blocklist",
            url = "https://adaway.org/hosts.txt",
            filterCount = 6541,
            sizeBytes = 243454L,
            lastUpdatedEpochMs = 1728412320000L,
            isEnabled = true,
            isCustom = false,
            category = FilterCategories.MOBILE_OEM,
            description = "Blocks in-app ads (AdMob, Unity, AppLovin, Vungle)."
        )

        val bundledList = listOf(newEasylist, newAdaway)

        val merged = ExtensionRepository.migrateExtensions(savedList, bundledList)

        // 1. Custom filter is preserved
        val mergedCustom = merged.find { it.id == "custom_123" }
        assertTrue(mergedCustom != null)
        assertTrue(mergedCustom!!.isCustom)
        assertEquals("My Blocklist", mergedCustom!!.name)

        // 2. Existing extension user toggle (false) is strictly preserved
        val mergedEasylist = merged.find { it.id == "easylist" }
        assertTrue(mergedEasylist != null)
        assertFalse(mergedEasylist!!.isEnabled)
        assertEquals("Primary ad-blocking filter list for global web traffic", mergedEasylist!!.description)

        // 3. New extension is added with manifest defaults
        val mergedAdaway = merged.find { it.id == "adaway_default" }
        assertTrue(mergedAdaway != null)
        assertTrue(mergedAdaway!!.isEnabled)
        assertEquals(FilterCategories.MOBILE_OEM, mergedAdaway!!.category)

        // 4. Obsolete aaklist is removed (not in bundled list and not custom)
        val mergedAak = merged.find { it.id == "aaklist" }
        assertNull(mergedAak)
    }

    @Test
    fun testMigrateExtensionsDoesNotDuplicateManualFilters() {
        val manualFilter = ExtensionFilter(
            id = "manual_filters",
            name = "Manual filters",
            url = "",
            isCustom = true,
            isEnabled = false // user turned it off
        )
        val userCustom = ExtensionFilter(
            id = "custom_999",
            name = "My Custom Filter",
            url = "https://custom.org/rules.txt",
            isCustom = true,
            isEnabled = true
        )
        val easylist = ExtensionFilter(
            id = "easylist",
            name = "EasyList",
            isCustom = false,
            isEnabled = true
        )
        val savedList = listOf(manualFilter, userCustom, easylist)

        val bundledManual = ExtensionFilter(
            id = "manual_filters",
            name = "Manual filters",
            url = "",
            isCustom = true,
            isEnabled = true
        )
        val bundledEasylist = ExtensionFilter(
            id = "easylist",
            name = "EasyList",
            isCustom = false,
            isEnabled = true
        )
        val bundledList = listOf(bundledManual, bundledEasylist)

        val merged = ExtensionRepository.migrateExtensions(savedList, bundledList)

        // Count of manual_filters must be exactly 1, not duplicated!
        assertEquals(1, merged.count { it.id == "manual_filters" })
        assertEquals(1, merged.count { it.id == "custom_999" })
        assertEquals(1, merged.count { it.id == "easylist" })
        assertEquals(3, merged.size)
        // User toggle state for manual_filters must be preserved
        assertFalse(merged.first { it.id == "manual_filters" }.isEnabled)
    }

    @Test
    fun testMigrateExtensionsPreservesLegacyOisdToggle() {
        val legacyOisd = ExtensionFilter(
            id = "oisd_abp_full",
            name = "OISD Abp Full",
            url = "https://small.oisd.nl/",
            isEnabled = false, // user disabled it
            isCustom = false
        )
        val savedList = listOf(legacyOisd)

        val oisdSmall = ExtensionFilter(
            id = "oisd_small",
            name = "OISD Small",
            url = "https://small.oisd.nl/",
            isEnabled = true,
            isCustom = false
        )
        val oisdBig = ExtensionFilter(
            id = "oisd_big",
            name = "OISD Big (Full)",
            url = "https://big.oisd.nl/",
            isEnabled = false,
            isCustom = false
        )
        val bundledList = listOf(oisdSmall, oisdBig)

        val merged = ExtensionRepository.migrateExtensions(savedList, bundledList)
        val small = merged.find { it.id == "oisd_small" }
        assertTrue(small != null)
        assertFalse(small!!.isEnabled)
    }
}
