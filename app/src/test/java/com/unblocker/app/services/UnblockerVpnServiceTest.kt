package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

class UnblockerVpnServiceTest {

    @Test
    fun testShouldWarnTruthTable() {
        // active = true, serverName = non-null -> warn
        assertEquals(true, UnblockerVpnService.Companion.shouldWarn(true, "dns.google"))
        
        // active = true, serverName = null -> no warn (fallback mode)
        assertEquals(false, UnblockerVpnService.Companion.shouldWarn(true, null))
        
        // active = false, serverName = non-null -> warn (user has it set, but not actively used for resolution yet)
        assertEquals(true, UnblockerVpnService.Companion.shouldWarn(false, "dns.google"))
        
        // active = false, serverName = null -> no warn
        assertEquals(false, UnblockerVpnService.Companion.shouldWarn(false, null))
    }
}
