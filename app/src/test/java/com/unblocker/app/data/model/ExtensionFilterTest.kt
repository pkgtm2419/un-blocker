package com.unblocker.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionFilterTest {

    @Test
    fun testFormattedSize() {
        val bytes = ExtensionFilter(
            id = "test_bytes",
            name = "Test",
            sizeBytes = 500L
        )
        assertEquals("500.0B", bytes.formattedSize())

        val kb = ExtensionFilter(
            id = "test_kb",
            name = "Test",
            sizeBytes = 722944L
        )
        assertEquals("706.0KB", kb.formattedSize())

        val mb = ExtensionFilter(
            id = "test_mb",
            name = "Test",
            sizeBytes = 1048576L
        )
        assertEquals("1.0MB", mb.formattedSize())

        val customZero = ExtensionFilter(
            id = "custom",
            name = "Custom",
            sizeBytes = 0L,
            isCustom = true
        )
        assertEquals("N/A", customZero.formattedSize())
    }

    @Test
    fun testFormattedLastUpdated() {
        val ext = ExtensionFilter(
            id = "test",
            name = "Test",
            lastUpdatedEpochMs = 1728412320000L // Oct 09, 2024
        )
        val formatted = ext.formattedLastUpdated()
        assertTrue(formatted.contains("Oct 09") || formatted.contains("2024") || formatted.contains("2026"))

        val customNoDate = ExtensionFilter(
            id = "custom",
            name = "Custom",
            lastUpdatedEpochMs = 0L,
            isCustom = true
        )
        assertEquals("N/A", customNoDate.formattedLastUpdated())
    }

    @Test
    fun testCategoryAndDescriptionDefaults() {
        val defaultFilter = ExtensionFilter(
            id = "test_default",
            name = "Default Filter"
        )
        assertEquals(FilterCategories.PRIVACY, defaultFilter.category)
        assertEquals("", defaultFilter.description)

        val customFilter = ExtensionFilter(
            id = "test_custom",
            name = "Custom Filter",
            category = FilterCategories.SECURITY,
            description = "High-priority malware feed"
        )
        assertEquals(FilterCategories.SECURITY, customFilter.category)
        assertEquals("High-priority malware feed", customFilter.description)
    }

    @Test
    fun testCategoriesList() {
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.ALL))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.RECOMMENDED))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.MASTER_DNS))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.MOBILE_OEM))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.SECURITY))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.PARENTAL))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.SMART_DEVICES))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.PRIVACY))
        assertTrue(FilterCategories.ALL_CATEGORIES.contains(FilterCategories.REGIONAL))
    }
}
