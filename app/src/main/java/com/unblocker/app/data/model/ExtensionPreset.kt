package com.unblocker.app.data.model

import android.os.Build
import java.util.Locale

/**
 * Curated filter list configuration presets balancing filtering rigor, memory footprint, and app compatibility.
 *
 * @property title User-facing display title for the preset.
 * @property description Concise explanation of filtering scope, tradeoffs, and target use case.
 */
enum class ExtensionPreset(
    val title: String,
    val description: String,
) {
    /**
     * Minimal, zero-breakage configuration preserving banking, shopping, and everyday app functionality.
     */
    ESSENTIAL(
        title = "Essential (Zero Breakage)",
        description = "Lightweight, zero false positives. Prevents breakage in banking, regional, and work apps.",
    ),

    /**
     * Balanced master blocking with mobile ad SDK rules, DoH bypass prevention, and device OEM telemetry filters.
     */
    RECOMMENDED(
        title = "Recommended (Balanced)",
        description = "Optimal ad and tracker suppression with mobile SDKs, OEM telemetry, and DoH bypass blocks.",
    ),

    /**
     * Maximum aggressive blocking including annoyances, social widgets, telemetry, and aggressive threat feeds.
     */
    AGGRESSIVE(
        title = "Aggressive (Maximum Shield)",
        description = "Full-coverage shield against mobile ads, in-app trackers, social widgets, and OEM profiling.",
    ),
    ;

    /**
     * Resolves the set of catalog extension IDs enabled by this preset.
     *
     * @param manufacturer Device manufacturer identifier (such as [Build.MANUFACTURER]) for OEM targeting.
     * @return Set of extension identifiers that should be enabled.
     */
    fun resolveTargetIds(manufacturer: String = Build.MANUFACTURER): Set<String> {
        val baseIds =
            when (this) {
                ESSENTIAL -> {
                    mutableSetOf(
                        "oisd_small",
                        "ublock_unbreak",
                        "adguard_dns_filter",
                        "malware_urlhaus_c2",
                    )
                }

                RECOMMENDED -> {
                    mutableSetOf(
                        "hagezi_multi_pro",
                        "adguard_dns_filter",
                        "adaway_default",
                        "easyprivacy",
                        "ublock_unbreak",
                        "hagezi_doh_bypass",
                        "malware_urlhaus_c2",
                    )
                }

                AGGRESSIVE -> {
                    mutableSetOf(
                        "hagezi_multi_pro",
                        "adguard_dns_filter",
                        "mobile_app_ads",
                        "adaway_default",
                        "easyprivacy",
                        "fanboy_social",
                        "ublock_annoyances",
                        "ublock_unbreak",
                        "hagezi_doh_bypass",
                        "threatfox_malware",
                        "malware_urlhaus_c2",
                        "phishing_army_extended",
                        "microsoft_mobile_telemetry",
                    )
                }
            }

        // OEM specific additions for non-minimal tiers
        if (this != ESSENTIAL) {
            val normalizedManufacturer = manufacturer.lowercase(Locale.ROOT)
            when {
                normalizedManufacturer.contains("samsung") -> baseIds.add("hagezi_native_samsung")

                normalizedManufacturer.contains("xiaomi") ||
                    normalizedManufacturer.contains("redmi") ||
                    normalizedManufacturer.contains("poco") -> baseIds.add("hagezi_native_xiaomi")

                normalizedManufacturer.contains("apple") -> baseIds.add("hagezi_native_apple")
            }
        }

        return baseIds
    }
}
