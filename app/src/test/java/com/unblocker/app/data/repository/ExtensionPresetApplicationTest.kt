package com.unblocker.app.data.repository

import com.unblocker.app.data.model.ExtensionFilter
import com.unblocker.app.data.model.ExtensionPreset
import com.unblocker.app.data.model.FilterCategories
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test coverage for [ExtensionRepository.computePresetExtensions].
 */
class ExtensionPresetApplicationTest {
    /**
     * Verifies that applying the Essential preset enables minimal lists, disables non-targets,
     * and strictly preserves user custom blocklists.
     */
    @Test
    fun applyEssentialPresetEnablesTargetsAndPreservesCustomFilters() {
        val custom =
            ExtensionFilter(
                id = "custom_user_list",
                name = "User Custom Domain Exceptions",
                url = "https://example.com/custom.txt",
                filterCount = 10,
                sizeBytes = 500L,
                lastUpdatedEpochMs = 1700000000000L,
                isEnabled = true,
                isCustom = true,
                category = FilterCategories.CUSTOM,
            )

        val oisdSmall =
            ExtensionFilter(
                id = "oisd_small",
                name = "OISD Small",
                url = "https://example.com/oisd.txt",
                filterCount = 10000,
                sizeBytes = 200000L,
                lastUpdatedEpochMs = 1700000000000L,
                isEnabled = false,
                isCustom = false,
                category = FilterCategories.RECOMMENDED,
            )

        val fanboySocial =
            ExtensionFilter(
                id = "fanboy_social",
                name = "Fanboy Social",
                url = "https://example.com/social.txt",
                filterCount = 5000,
                sizeBytes = 100000L,
                lastUpdatedEpochMs = 1700000000000L,
                isEnabled = true, // Currently enabled, should become disabled in Essential
                isCustom = false,
                category = FilterCategories.PRIVACY,
            )

        val catalog = listOf(custom, oisdSmall, fanboySocial)

        val result =
            ExtensionRepository.computePresetExtensions(
                current = catalog,
                preset = ExtensionPreset.ESSENTIAL,
                manufacturer = "Google",
            )

        // Custom filter must remain untouched (enabled = true)
        val resultCustom = result.first { it.id == "custom_user_list" }
        assertTrue(resultCustom.isEnabled)

        // OISD small is in Essential target list -> must become enabled
        val resultOisd = result.first { it.id == "oisd_small" }
        assertTrue(resultOisd.isEnabled)

        // Fanboy social is NOT in Essential target list -> must become disabled
        val resultSocial = result.first { it.id == "fanboy_social" }
        assertFalse(resultSocial.isEnabled)
    }

    /**
     * Verifies that applying the Recommended preset incorporates OEM list based on manufacturer.
     */
    @Test
    fun applyRecommendedPresetIncludesOemTarget() {
        val samsungOem =
            ExtensionFilter(
                id = "hagezi_native_samsung",
                name = "Samsung OEM Telemetry",
                url = "https://example.com/samsung.txt",
                filterCount = 800,
                sizeBytes = 15000L,
                lastUpdatedEpochMs = 1700000000000L,
                isEnabled = false,
                isCustom = false,
                category = FilterCategories.MOBILE_OEM,
            )

        val catalog = listOf(samsungOem)

        val samsungResult =
            ExtensionRepository.computePresetExtensions(
                current = catalog,
                preset = ExtensionPreset.RECOMMENDED,
                manufacturer = "Samsung",
            )
        assertTrue(samsungResult.first().isEnabled)

        val googleResult =
            ExtensionRepository.computePresetExtensions(
                current = catalog,
                preset = ExtensionPreset.RECOMMENDED,
                manufacturer = "Google",
            )
        assertFalse(googleResult.first().isEnabled)
    }
}
