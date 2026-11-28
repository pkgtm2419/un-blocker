package com.unblocker.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies validation, deduplication, and self-exclusion of per-app bypass lists (WP-23 / F32).
 */
class ExcludedPackagesPreferenceTest {
    @Test
    fun normalizeExcludedPackagesFiltersOwnInvalidAndDuplicateEntries() {
        val cleaned =
            FilteringPreferences.normalizeExcludedPackages(
                raw =
                    setOf(
                        " com.example.bank ",
                        "com.unblocker.app",
                        "invalid package!",
                        "singleword",
                        "com.example.lan_tool",
                        "",
                    ),
                ownPackage = "com.unblocker.app",
            )

        assertEquals(setOf("com.example.bank", "com.example.lan_tool"), cleaned)
    }

    @Test
    fun normalizeExcludedPackagesReturnsEmptyOnNullInput() {
        assertEquals(
            emptySet<String>(),
            FilteringPreferences.normalizeExcludedPackages(null, "com.unblocker.app"),
        )
    }
}
