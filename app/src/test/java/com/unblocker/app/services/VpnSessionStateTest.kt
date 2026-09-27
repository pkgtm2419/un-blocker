package com.unblocker.app.services

import org.junit.Assert.*
import org.junit.Test

class VpnSessionStateTest {
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

    @Test fun lateEstablishmentCannotReactivateStoppedSession() {
        val state = VpnSessionState()
        val owner = state.begin()
        state.stop()
        assertFalse(state.established(owner))
        assertEquals(ServiceStatus.STOPPED, state.status.value)
    }

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

    @Test fun bootRequiresAllThreeConditions() {
        for (restart in listOf(false, true)) for (enabled in listOf(false, true)) {
            for (permission in listOf(false, true)) {
                assertEquals(restart && enabled && permission,
                    BootPolicy.shouldStart(restart, enabled, permission))
            }
        }
    }
}
