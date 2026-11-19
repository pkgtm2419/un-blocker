package com.unblocker.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test coverage for [ExtensionPreset] target ID resolution and OEM list detection.
 */
class ExtensionPresetTest {
    /**
     * Verifies that the Essential preset targets zero-breakage baseline lists and omits OEM extras.
     */
    @Test
    fun essentialPresetTargetsMinimalBaseline() {
        val targets = ExtensionPreset.ESSENTIAL.resolveTargetIds(manufacturer = "Samsung")

        assertTrue(targets.contains("oisd_small"))
        assertTrue(targets.contains("ublock_unbreak"))
        assertTrue(targets.contains("adguard_dns_filter"))
        assertTrue(targets.contains("malware_urlhaus_c2"))

        // Essential must never include aggressive or OEM lists
        assertFalse(targets.contains("hagezi_native_samsung"))
        assertFalse(targets.contains("fanboy_social"))
        assertFalse(targets.contains("ublock_annoyances"))
    }

    /**
     * Verifies that the Recommended preset includes balanced master, DoH bypass, and manufacturer lists.
     */
    @Test
    fun recommendedPresetIncludesDoHBypassAndSamsungOem() {
        val targets = ExtensionPreset.RECOMMENDED.resolveTargetIds(manufacturer = "Samsung")

        assertTrue(targets.contains("hagezi_multi_pro"))
        assertTrue(targets.contains("adguard_dns_filter"))
        assertTrue(targets.contains("adaway_default"))
        assertTrue(targets.contains("easyprivacy"))
        assertTrue(targets.contains("ublock_unbreak"))
        assertTrue(targets.contains("hagezi_doh_bypass"))
        assertTrue(targets.contains("malware_urlhaus_c2"))
        assertTrue(targets.contains("hagezi_native_samsung"))

        assertFalse(targets.contains("hagezi_native_xiaomi"))
    }

    /**
     * Verifies that Xiaomi/Redmi/Poco OEM devices resolve the Xiaomi telemetry list in Recommended tier.
     */
    @Test
    fun recommendedPresetResolvesXiaomiVariants() {
        val xiaomiTargets = ExtensionPreset.RECOMMENDED.resolveTargetIds(manufacturer = "Xiaomi")
        assertTrue(xiaomiTargets.contains("hagezi_native_xiaomi"))

        val redmiTargets = ExtensionPreset.RECOMMENDED.resolveTargetIds(manufacturer = "Redmi")
        assertTrue(redmiTargets.contains("hagezi_native_xiaomi"))

        val pocoTargets = ExtensionPreset.RECOMMENDED.resolveTargetIds(manufacturer = "POCO")
        assertTrue(pocoTargets.contains("hagezi_native_xiaomi"))
    }

    /**
     * Verifies that the Aggressive preset includes full social, annoyance, and threat coverage.
     */
    @Test
    fun aggressivePresetIncludesFullAnnoyanceAndThreatCoverage() {
        val targets = ExtensionPreset.AGGRESSIVE.resolveTargetIds(manufacturer = "Google")

        assertTrue(targets.contains("hagezi_multi_pro"))
        assertTrue(targets.contains("adguard_dns_filter"))
        assertTrue(targets.contains("mobile_app_ads"))
        assertTrue(targets.contains("adaway_default"))
        assertTrue(targets.contains("easyprivacy"))
        assertTrue(targets.contains("fanboy_social"))
        assertTrue(targets.contains("ublock_annoyances"))
        assertTrue(targets.contains("ublock_unbreak"))
        assertTrue(targets.contains("hagezi_doh_bypass"))
        assertTrue(targets.contains("threatfox_malware"))
        assertTrue(targets.contains("malware_urlhaus_c2"))
        assertTrue(targets.contains("phishing_army_extended"))
        assertTrue(targets.contains("microsoft_mobile_telemetry"))
    }
}
