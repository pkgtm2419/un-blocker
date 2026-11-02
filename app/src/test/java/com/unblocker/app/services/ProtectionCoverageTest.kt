package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionCoverageTest {
    @Test
    fun computesExpectedProtectionCoverage() {
        assertEquals(
            ProtectionCoverage.OFF,
            ProtectionCoverage.compute(ServiceStatus.STOPPED, isPrivateDnsActive = false, hasUsableResolvers = true)
        )
        assertEquals(
            ProtectionCoverage.OFF,
            ProtectionCoverage.compute(ServiceStatus.STARTING, isPrivateDnsActive = false, hasUsableResolvers = true)
        )
        assertEquals(
            ProtectionCoverage.LIMITED_PRIVATE_DNS,
            ProtectionCoverage.compute(ServiceStatus.RUNNING, isPrivateDnsActive = true, hasUsableResolvers = true)
        )
        assertEquals(
            ProtectionCoverage.FULL_SYSTEM_DNS,
            ProtectionCoverage.compute(ServiceStatus.RUNNING, isPrivateDnsActive = false, hasUsableResolvers = true)
        )
        assertEquals(
            ProtectionCoverage.UNKNOWN_RESOLVER,
            ProtectionCoverage.compute(ServiceStatus.RUNNING, isPrivateDnsActive = false, hasUsableResolvers = false)
        )
    }
}
