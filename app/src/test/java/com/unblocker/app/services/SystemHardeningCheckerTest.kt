package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test coverage for [HardeningPosture] validation and intent creation in [SystemHardeningChecker].
 */
class SystemHardeningCheckerTest {
    /**
     * Verifies that posture is fully hardened only when battery optimization is ignored and Private DNS is inactive.
     */
    @Test
    fun hardeningPostureReflectsSecurityAlignment() {
        val fullyHardened =
            HardeningPosture(
                isBatteryOptimizationIgnored = true,
                isPrivateDnsActive = false,
                privateDnsServerName = null,
                manufacturer = "Google",
            )
        assertTrue(fullyHardened.isFullyHardened)

        val leakingPrivateDns =
            HardeningPosture(
                isBatteryOptimizationIgnored = true,
                isPrivateDnsActive = true,
                privateDnsServerName = "dns.google",
                manufacturer = "Google",
            )
        assertFalse(leakingPrivateDns.isFullyHardened)

        val unexemptBattery =
            HardeningPosture(
                isBatteryOptimizationIgnored = false,
                isPrivateDnsActive = false,
                privateDnsServerName = null,
                manufacturer = "Samsung",
            )
        assertFalse(unexemptBattery.isFullyHardened)
    }

    /**
     * Verifies that navigation actions and Play Store URIs match expected constants.
     */
    @Test
    fun constantsAndUrlFactoriesProduceExpectedOutputs() {
        assertEquals("android.net.vpn.SETTINGS", SystemHardeningChecker.ACTION_VPN_SETTINGS)
        assertEquals("com.google.android.gms.settings.ADS_PRIVACY", SystemHardeningChecker.ACTION_AD_PRIVACY)

        val playStoreUrl = SystemHardeningChecker.playStoreUrl("org.mozilla.firefox")
        assertEquals("https://play.google.com/store/apps/details?id=org.mozilla.firefox", playStoreUrl)
    }
}
