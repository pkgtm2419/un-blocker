package com.unblocker.app.ui.model

import com.unblocker.app.services.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for protection ui state mapper.
 */
class ProtectionUiStateMapperTest {
    /**
     * Verifies that test vpn stopped returns off state with start action.
     */
    @Test
    fun testVpnStoppedReturnsOffStateWithStartAction() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.STOPPED,
                privateDnsActive = false,
                resolverAvailable = true,
            )
        assertEquals("Off", state.label)
        assertEquals(ServiceStatus.STOPPED, state.status)
        assertEquals(RecoveryAction.START_VPN, state.action)
    }

    /**
     * Verifies that test vpn starting returns starting state without action.
     */
    @Test
    fun testVpnStartingReturnsStartingStateWithoutAction() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.STARTING,
                privateDnsActive = false,
                resolverAvailable = true,
            )
        assertEquals("Starting", state.label)
        assertEquals(ServiceStatus.STARTING, state.status)
        assertNull(state.action)
    }

    /**
     * Verifies that test vpn running with regular DNS returns protected standard DNS.
     */
    @Test
    fun testVpnRunningWithRegularDnsReturnsProtectedStandardDns() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.RUNNING,
                privateDnsActive = false,
                resolverAvailable = true,
            )
        assertEquals("Protected (standard DNS)", state.label)
        assertEquals(ServiceStatus.RUNNING, state.status)
        assertNull(state.action)
    }

    /**
     * Verifies that test vpn running with strict private DNS returns limited coverage with settings action.
     */
    @Test
    fun testVpnRunningWithStrictPrivateDnsReturnsLimitedCoverageWithSettingsAction() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.RUNNING,
                privateDnsActive = true,
                resolverAvailable = true,
            )
        assertEquals("Limited coverage (Private DNS)", state.label)
        assertEquals(ServiceStatus.RUNNING, state.status)
        assertEquals(RecoveryAction.OPEN_PRIVATE_DNS_SETTINGS, state.action)
    }

    /**
     * Verifies that test vpn running with no resolvers returns limited coverage with retry action.
     */
    @Test
    fun testVpnRunningWithNoResolversReturnsLimitedCoverageWithRetryAction() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.RUNNING,
                privateDnsActive = false,
                resolverAvailable = false,
            )
        assertEquals("Limited coverage (no DNS servers)", state.label)
        assertEquals(ServiceStatus.RUNNING, state.status)
        assertEquals(RecoveryAction.RETRY, state.action)
    }

    /**
     * Verifies that test vpn error returns error state with retry action.
     */
    @Test
    fun testVpnErrorReturnsErrorStateWithRetryAction() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.ERROR,
                privateDnsActive = false,
                resolverAvailable = true,
            )
        assertEquals("Error", state.label)
        assertEquals(ServiceStatus.ERROR, state.status)
        assertEquals(RecoveryAction.RETRY, state.action)
    }

    /**
     * Verifies that test vpn stopping returns off state.
     */
    @Test
    fun testVpnStoppingReturnsOffState() {
        val state =
            ProtectionUiStateMapper.from(
                status = ServiceStatus.STOPPING,
                privateDnsActive = false,
                resolverAvailable = true,
            )
        assertEquals("Off", state.label)
        assertEquals(ServiceStatus.STOPPING, state.status)
        assertNull(state.action)
    }
}
