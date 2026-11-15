package com.unblocker.app.services

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression coverage for vpn session state.
 */
class VpnSessionStateTest {
    /**
     * Verifies that previous worker cannot change new session.
     */
    @Test fun previousWorkerCannotChangeNewSession() {
        val state = VpnSessionState()
        val old = state.begin()
        state.stop(old)
        val current = state.begin()
        assertFalse(state.established(old))
        state.failed(old)
        state.stop(old)
        assertEquals(ServiceStatus.STARTING, state.status.value)
        assertTrue(state.established(current))
        assertTrue(state.active.value)
    }

    /**
     * Verifies that only established session reports running.
     */
    @Test fun onlyEstablishedSessionReportsRunning() {
        val state = VpnSessionState()
        val owner = state.begin()
        assertEquals(ServiceStatus.STARTING, state.status.value)
        assertFalse(state.active.value)
        state.established(owner)
        assertEquals(ServiceStatus.RUNNING, state.status.value)
        state.failed()
        assertEquals(ServiceStatus.ERROR, state.status.value)
        val next = state.begin()
        assertTrue(state.established(next))
    }

    /**
     * Verifies that late establishment cannot reactivate stopped session.
     */
    @Test fun lateEstablishmentCannotReactivateStoppedSession() {
        val state = VpnSessionState()
        val owner = state.begin()
        state.stop()
        assertFalse(state.established(owner))
        assertEquals(ServiceStatus.STOPPED, state.status.value)
    }

    /**
     * Verifies that stopping is inactive until destruction publishes stopped.
     */
    @Test fun stoppingIsInactiveUntilDestructionPublishesStopped() {
        val state = VpnSessionState()
        val owner = state.begin()
        assertTrue(state.established(owner))

        state.stopping(owner)
        assertEquals(ServiceStatus.STOPPING, state.status.value)
        assertFalse(state.active.value)

        state.stop(owner)
        assertEquals(ServiceStatus.STOPPED, state.status.value)
    }

    /**
     * Verifies that boot requires all three conditions.
     */
    @Test fun bootRequiresAllThreeConditions() {
        for (restart in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                for (permission in listOf(false, true)) {
                    assertEquals(
                        restart && enabled && permission,
                        BootPolicy.shouldStart(restart, enabled, permission),
                    )
                }
            }
        }
    }
}
