package com.unblocker.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.unblocker.app.R
import java.util.Locale

/**
 * Logical grouping tiers presented on the Settings & Shields overview screen (WP-13 / F28).
 *
 * @property titleRes Localized section group heading resource.
 */
enum class SettingsSectionGroup(
    @param:StringRes val titleRes: Int,
) {
    CORE(R.string.settings_group_core),
    DATA(R.string.settings_group_data),
    SYSTEM(R.string.settings_group_system),
}

/**
 * Modular settings sub-screens mapping 1-to-1 with `DeepLinkParser.allowedSettingsSections`.
 *
 * @property slug Canonical URI section slug (`unblocker://settings/<slug>`).
 * @property group Overview group containing this section card.
 * @property titleRes Localized display title resource.
 * @property subtitleRes Localized short summary resource.
 * @property icon Material icon displayed on section cards and detail headers.
 * @property searchKeywords Non-UI search aliases for quick filtering across technical and common terms.
 */
enum class SettingsSection(
    val slug: String,
    val group: SettingsSectionGroup,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val searchKeywords: List<String>,
) {
    PROTECTION(
        slug = "protection",
        group = SettingsSectionGroup.CORE,
        titleRes = R.string.settings_section_protection_title,
        subtitleRes = R.string.settings_section_protection_subtitle,
        icon = Icons.Default.Security,
        searchKeywords = listOf("tier", "basic", "optimal", "complete", "adult", "18+", "smart", "approved", "policy"),
    ),
    FILTERS(
        slug = "filters",
        group = SettingsSectionGroup.CORE,
        titleRes = R.string.settings_section_filters_title,
        subtitleRes = R.string.settings_section_filters_subtitle,
        icon = Icons.Default.FilterList,
        searchKeywords = listOf("hosts", "ublock", "easylist", "adguard", "extensions", "presets", "blocklist"),
    ),
    RULES(
        slug = "rules",
        group = SettingsSectionGroup.CORE,
        titleRes = R.string.settings_section_rules_title,
        subtitleRes = R.string.settings_section_rules_subtitle,
        icon = Icons.AutoMirrored.Filled.Rule,
        searchKeywords = listOf("exceptions", "allowlist", "whitelist", "blacklist", "custom", "domains"),
    ),
    LEARNING(
        slug = "learning",
        group = SettingsSectionGroup.DATA,
        titleRes = R.string.settings_section_learning_title,
        subtitleRes = R.string.settings_section_learning_subtitle,
        icon = Icons.Default.Psychology,
        searchKeywords = listOf("evidence", "hmac", "analytics", "breakage", "statistics", "charts", "stats"),
    ),
    PRIVACY(
        slug = "privacy",
        group = SettingsSectionGroup.DATA,
        titleRes = R.string.settings_section_privacy_title,
        subtitleRes = R.string.settings_section_privacy_subtitle,
        icon = Icons.Default.Shield,
        searchKeywords = listOf("logs", "pause", "export", "csv", "clear", "history", "telemetry", "recents"),
    ),
    AUTOMATION(
        slug = "automation",
        group = SettingsSectionGroup.SYSTEM,
        titleRes = R.string.settings_section_automation_title,
        subtitleRes = R.string.settings_section_automation_subtitle,
        icon = Icons.Default.Build,
        searchKeywords = listOf("boot", "restart", "watchdog", "health check", "reliability", "interval"),
    ),
    HEALTH(
        slug = "health",
        group = SettingsSectionGroup.SYSTEM,
        titleRes = R.string.settings_section_health_title,
        subtitleRes = R.string.settings_section_health_subtitle,
        icon = Icons.Default.HealthAndSafety,
        searchKeywords = listOf("private dns", "dot", "battery", "always-on", "vpn", "hardening", "diagnostics"),
    ),
    APPEARANCE(
        slug = "appearance",
        group = SettingsSectionGroup.SYSTEM,
        titleRes = R.string.settings_section_appearance_title,
        subtitleRes = R.string.settings_section_appearance_subtitle,
        icon = Icons.Default.AutoAwesome,
        searchKeywords = listOf("beyond dns", "youtube", "onboarding", "setup", "theme", "guide", "language"),
    ),
    ABOUT(
        slug = "about",
        group = SettingsSectionGroup.SYSTEM,
        titleRes = R.string.settings_section_about_title,
        subtitleRes = R.string.settings_section_about_subtitle,
        icon = Icons.Default.Info,
        searchKeywords = listOf("version", "github", "license", "apache", "open source", "privacy"),
    ),
}

/**
 * Pure Kotlin section lookup and search filter helper used by both Settings UI and JVM unit tests.
 */
object SettingsSectionCatalog {
    /** Canonical set of slugs expected by `DeepLinkParser`. */
    val canonicalSlugs: Set<String> = SettingsSection.entries.mapTo(linkedSetOf()) { it.slug }

    /**
     * Resolves an optional URI/navigation slug to its [SettingsSection], returning null if unrecognized.
     */
    fun resolveSection(rawSlug: String?): SettingsSection? {
        val normalized = rawSlug?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (normalized.isEmpty()) return null
        return SettingsSection.entries.firstOrNull { it.slug == normalized }
    }

    /**
     * Filters [SettingsSection.entries] matching [query] across localized labels, slugs, and keywords.
     */
    fun filterSections(
        query: String,
        titleForSection: (SettingsSection) -> String,
        subtitleForSection: (SettingsSection) -> String,
    ): List<SettingsSection> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return SettingsSection.entries

        return SettingsSection.entries.filter { section ->
            section.slug.contains(needle) ||
                titleForSection(section).lowercase(Locale.ROOT).contains(needle) ||
                subtitleForSection(section).lowercase(Locale.ROOT).contains(needle) ||
                section.searchKeywords.any { it.contains(needle) }
        }
    }
}
