package com.unblocker.app.ui.settings

import com.unblocker.app.ui.navigation.DeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Settings IA sections, deep-link alignment, and search filtering (WP-13 / F28).
 */
class SettingsSectionCatalogTest {
    @Test
    fun allDeepLinkSectionsMapOneToOneWithSettingsSections() {
        assertEquals(DeepLinkParser.allowedSettingsSections, SettingsSectionCatalog.canonicalSlugs)
        assertEquals(9, SettingsSection.entries.size)
        assertEquals(
            9,
            SettingsSection.entries
                .map { it.slug }
                .toSet()
                .size,
        )
    }

    @Test
    fun resolveSectionHandlesCaseAndRejectsUnknownSlugs() {
        assertEquals(SettingsSection.PROTECTION, SettingsSectionCatalog.resolveSection("protection"))
        assertEquals(SettingsSection.PRIVACY, SettingsSectionCatalog.resolveSection(" PRIVACY "))
        assertNull(SettingsSectionCatalog.resolveSection(null))
        assertNull(SettingsSectionCatalog.resolveSection(""))
        assertNull(SettingsSectionCatalog.resolveSection("unknown-section"))
    }

    @Test
    fun searchFiltersByTitleSubtitleOrTechnicalKeywords() {
        val titles = SettingsSection.entries.associateWith { it.name.lowercase() }
        val subtitles = SettingsSection.entries.associateWith { it.slug }

        val blankResults = SettingsSectionCatalog.filterSections("", { titles.getValue(it) }, { subtitles.getValue(it) })
        assertEquals(SettingsSection.entries, blankResults)

        val csvResults = SettingsSectionCatalog.filterSections("csv", { titles.getValue(it) }, { subtitles.getValue(it) })
        assertEquals(listOf(SettingsSection.PRIVACY), csvResults)

        val dnsResults = SettingsSectionCatalog.filterSections("private dns", { titles.getValue(it) }, { subtitles.getValue(it) })
        assertTrue(dnsResults.contains(SettingsSection.HEALTH))

        val noResults = SettingsSectionCatalog.filterSections("zzzzzz", { titles.getValue(it) }, { subtitles.getValue(it) })
        assertTrue(noResults.isEmpty())
    }
}
