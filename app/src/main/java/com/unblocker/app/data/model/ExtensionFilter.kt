package com.unblocker.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter list category constants.
 */
object FilterCategories {
    /**
     * Category label selecting all extension entries.
     */
    const val ALL = "All"
    /**
     * Category label selecting the recommended subset.
     */
    const val RECOMMENDED = "Recommended"
    /**
     * Category label for consolidated DNS lists.
     */
    const val MASTER_DNS = "Master DNS"
    /**
     * Category label for mobile and device-vendor lists.
     */
    const val MOBILE_OEM = "Mobile & OEM"
    /**
     * Category label for security lists.
     */
    const val SECURITY = "Security"
    /**
     * Category label for parental filtering lists.
     */
    const val PARENTAL = "Parental"
    /**
     * Category label for connected-device lists.
     */
    const val SMART_DEVICES = "Smart Devices"
    /**
     * Category label for privacy lists.
     */
    const val PRIVACY = "Privacy"
    /**
     * Category label for region-specific lists.
     */
    const val REGIONAL = "Regional"
    /**
     * Category label for user-created lists.
     */
    const val CUSTOM = "Custom"

    /**
     * Ordered category tabs offered by the extension catalog, excluding the custom-only label.
     */
    val ALL_CATEGORIES = listOf(
        ALL,
        RECOMMENDED,
        MASTER_DNS,
        MOBILE_OEM,
        SECURITY,
        PARENTAL,
        SMART_DEVICES,
        PRIVACY,
        REGIONAL
    )
}

/**
 * Catalog metadata for a bundled or user-created filter list.
 *
 * @property id Stable catalog identifier used for preferences and cached filenames.
 * @property name User-visible list name.
 * @property url Source URL, or an empty string for entries without a download source.
 * @property filterCount Last known admitted rule count.
 * @property sizeBytes Last recorded downloaded response size in bytes.
 * @property lastUpdatedEpochMs Last successful update timestamp in Unix epoch milliseconds; zero means unknown.
 * @property isEnabled Whether this entry contributes cached rules to the active store.
 * @property isCustom Whether this entry was created by the user.
 * @property syntax Catalog syntax label.
 * @property category Catalog grouping label.
 * @property description User-visible description.
 * @property browserOnly Whether the catalog marks the list as browser-specific.
 */
data class ExtensionFilter(
    val id: String,
    val name: String,
    val url: String = "",
    val filterCount: Int = 0,
    val sizeBytes: Long = 0L,
    val lastUpdatedEpochMs: Long = 0L,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val syntax: String = "rules",
    val category: String = FilterCategories.PRIVACY,
    val description: String = "",
    val browserOnly: Boolean = false
) {
    /**
     * Formats the recorded size with binary conversion and B, KB, or MB display labels.
     *
     * @return US-locale size text, or the appropriate missing-size placeholder.
     */
    fun formattedSize(): String {
        if (sizeBytes <= 0L) {
            return if (isCustom) "N/A" else "0.0KB"
        }
        val kibibytes = sizeBytes / 1024.0
        return if (kibibytes < 1.0) {
            "${sizeBytes}.0B"
        } else if (kibibytes < 1024.0) {
            String.format(Locale.US, "%.1fKB", kibibytes)
        } else {
            val mebibytes = kibibytes / 1024.0
            String.format(Locale.US, "%.1fMB", mebibytes)
        }
    }

    /**
     * Formats the update timestamp in the device default time zone.
     *
     * @return US-locale date and time, or the appropriate missing-date placeholder.
     */
    fun formattedLastUpdated(): String {
        if (lastUpdatedEpochMs <= 0L) {
            return if (isCustom) "N/A" else "Never"
        }
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
        return dateFormat.format(Date(lastUpdatedEpochMs))
    }
}
