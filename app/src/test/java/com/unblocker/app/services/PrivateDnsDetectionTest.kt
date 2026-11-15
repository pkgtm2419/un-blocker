package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for private DNS detection.
 */
class PrivateDnsDetectionTest {

    /**
     * Resets the fixture state before each test to keep assertions independent.
     */
    @Before
    fun setup() {
        UnblockerVpnService.updatePrivateDnsState(false, null)
    }

    /**
     * Verifies that initial state is inactive.
     */
    @Test
    fun initialStateIsInactive() {
        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }

    /**
     * Verifies that null link properties sets inactive.
     */
    @Test
    fun nullLinkPropertiesSetsInactive() {
        UnblockerVpnService.updatePrivateDnsState(null as android.net.LinkProperties?)
        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }

    /**
     * Verifies that active private DNS updates state flow.
     */
    @Test
    fun activePrivateDnsUpdatesStateFlow() {
        UnblockerVpnService.updatePrivateDnsState(active = true, serverName = "dns.quad9.net")

        assertTrue(UnblockerVpnService.isPrivateDnsActive.value)
        assertEquals("dns.quad9.net", UnblockerVpnService.privateDnsServerName.value)

        // Tear down resets state
        UnblockerVpnService.updatePrivateDnsState(active = false, serverName = null)
        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }

    /**
     * Verifies that inactive private DNS leaves inactive.
     */
    @Test
    fun inactivePrivateDnsLeavesInactive() {
        UnblockerVpnService.updatePrivateDnsState(active = false, serverName = null)

        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }
}
