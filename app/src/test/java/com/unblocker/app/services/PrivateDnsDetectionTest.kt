package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrivateDnsDetectionTest {

    @Before
    fun setup() {
        UnblockerVpnService.updatePrivateDnsState(false, null)
    }

    @Test
    fun initialStateIsInactive() {
        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }

    @Test
    fun nullLinkPropertiesSetsInactive() {
        UnblockerVpnService.updatePrivateDnsState(null as android.net.LinkProperties?)
        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }

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

    @Test
    fun inactivePrivateDnsLeavesInactive() {
        UnblockerVpnService.updatePrivateDnsState(active = false, serverName = null)

        assertFalse(UnblockerVpnService.isPrivateDnsActive.value)
        assertNull(UnblockerVpnService.privateDnsServerName.value)
    }
}
