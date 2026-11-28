package com.unblocker.app.data.model

import android.os.Build
import androidx.annotation.StringRes
import com.unblocker.app.R
import java.util.Locale

/**
 * Curated filter list configuration presets balancing filtering rigor, memory footprint, and app compatibility.
 *
 * @property titleRes String resource ID for the user-facing display title.
 * @property shortTitleRes String resource ID for the compact selector pill title.
 * @property subtitleBadgeRes String resource ID for the compact selector pill subtitle badge.
 * @property descriptionRes String resource ID for the filtering scope, tradeoffs, and target use case.
 */
enum class ExtensionPreset(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortTitleRes: Int,
    @param:StringRes val subtitleBadgeRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    /**
     * Minimal, zero-breakage configuration preserving banking, shopping, and everyday app functionality.
     */
    ESSENTIAL(
        titleRes = R.string.preset_essential_title,
        shortTitleRes = R.string.preset_essential_short,
        subtitleBadgeRes = R.string.preset_essential_badge,
        descriptionRes = R.string.preset_essential_desc,
    ),

    /**
     * Balanced master blocking with mobile ad SDK rules, DoH bypass prevention, and device OEM telemetry filters.
     */
    RECOMMENDED(
        titleRes = R.string.preset_recommended_title,
        shortTitleRes = R.string.preset_recommended_short,
        subtitleBadgeRes = R.string.preset_recommended_badge,
        descriptionRes = R.string.preset_recommended_desc,
    ),

    /**
     * Maximum aggressive blocking including annoyances, social widgets, telemetry, and aggressive threat feeds.
     */
    AGGRESSIVE(
        titleRes = R.string.preset_aggressive_title,
        shortTitleRes = R.string.preset_aggressive_short,
        subtitleBadgeRes = R.string.preset_aggressive_badge,
        descriptionRes = R.string.preset_aggressive_desc,
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
