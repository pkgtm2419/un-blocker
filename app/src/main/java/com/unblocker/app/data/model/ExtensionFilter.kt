package com.unblocker.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model representing a content-filtering extension / hosts list.
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
    val syntax: String = "rules"
) {
    fun formattedSize(): String {
        if (sizeBytes <= 0L) {
            return if (isCustom) "N/A" else "0.0KB"
        }
        val kb = sizeBytes / 1024.0
        return if (kb < 1.0) {
            "${sizeBytes}.0B"
        } else if (kb < 1024.0) {
            String.format(Locale.US, "%.1fKB", kb)
        } else {
            val mb = kb / 1024.0
            String.format(Locale.US, "%.1fMB", mb)
        }
    }

    fun formattedLastUpdated(): String {
        if (lastUpdatedEpochMs <= 0L) {
            return if (isCustom) "N/A" else "Never"
        }
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
        return sdf.format(Date(lastUpdatedEpochMs))
    }
}
