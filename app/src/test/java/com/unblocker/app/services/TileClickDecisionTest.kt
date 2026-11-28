package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies Quick Settings Tile and Widget click decisions respect Android VPN consent (WP-30 / WP-32).
 */
class TileClickDecisionTest {
    @Test
    fun missingConsentRequestsInAppSetupEvenWhenInactive() {
        assertEquals(
            TileClickDecision.REQUEST_CONSENT_IN_APP,
            VpnControlActions.resolveToggleDecision(hasVpnConsent = false, isProtectionActive = false),
        )
    }

    @Test
    fun consentedInactiveStartsAndActiveStops() {
        assertEquals(
            TileClickDecision.START_PROTECTION,
            VpnControlActions.resolveToggleDecision(hasVpnConsent = true, isProtectionActive = false),
        )
        assertEquals(
            TileClickDecision.STOP_PROTECTION,
            VpnControlActions.resolveToggleDecision(hasVpnConsent = true, isProtectionActive = true),
        )
    }
}
