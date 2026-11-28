package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for [RecordingSessionManager] under isolated-tunnel attribution rules (WP-02).
 */
class RecordingSessionManagerTest {
    private lateinit var manager: RecordingSessionManager

    /**
     * Resets the fixture state before each test to keep assertions independent.
     */
    @Before
    fun setup() {
        manager = RecordingSessionManager.instance
        manager.stopSession()
    }

    /**
     * Verifies that active isolated sessions record all valid domains regardless of reported UID,
     * since tunnel isolation restricts traffic to the target app by construction.
     */
    @Test
    fun activeSessionRecordsAllSeenDomainsWithoutUidFiltering() {
        assertFalse(manager.isRecordingActive())
        val session = manager.startSession("com.example.app", 1042)
        assertTrue(manager.isRecordingActive())
        assertEquals("com.example.app", session.targetPackage)
        assertEquals(1042, session.targetUid)

        // Domain with matching UID is recorded
        assertTrue(manager.recordDomain("api.example.com", 1042))

        // Domain with different or null UID is ALSO recorded (no UID dropping in isolated tunnel)
        assertTrue(manager.recordDomain("cdn.example.com", 9999))
        assertTrue(manager.recordDomain("metrics.example.com", null))
        assertTrue(manager.recordDomain("no-uid.example.com"))

        // Duplicate domain increments count cleanly
        assertTrue(manager.recordDomain("API.EXAMPLE.COM."))

        val domains = manager.getRecordedDomains()
        assertEquals(4, domains.size)
        val apiDomain = domains.first { it.domain == "api.example.com" }
        assertEquals(2, apiDomain.count)

        val summary = manager.stopSession()
        assertFalse(manager.isRecordingActive())
        assertEquals(4, summary.size)
    }

    /**
     * Verifies that invalid hostnames or stopped sessions are never recorded.
     */
    @Test
    fun stoppedSessionOrMalformedHostIsIgnored() {
        assertFalse(manager.recordDomain("api.example.com"))

        manager.startSession("com.example.app")
        assertFalse(manager.recordDomain(""))
        assertFalse(manager.recordDomain("invalid host with spaces"))
        assertTrue(manager.getRecordedDomains().isEmpty())
    }

    /**
     * Verifies that sessions automatically stop once [RecordingSession.maxDurationMs] elapses.
     */
    @Test
    fun sessionAutoExpiresAfterMaxDuration() {
        manager.startSession("com.example.short", 1001, maxDurationMs = 1L)
        Thread.sleep(10)
        assertFalse(manager.isRecordingActive())
        assertNull(manager.activeSession.value)
        assertFalse(manager.recordDomain("late.example.com"))
    }

    /**
     * Verifies idle detection reports inactivity after threshold and resets when DNS traffic arrives.
     */
    @Test
    fun idleDetectionRespondsToActivity() {
        assertFalse(manager.isSessionIdle(0L))

        manager.startSession("com.example.idle")
        Thread.sleep(15)
        assertTrue(manager.isSessionIdle(durationMs = 10L))

        // Recording a domain refreshes lastActivityEpochMs
        assertTrue(manager.recordDomain("active.example.com"))
        assertFalse(manager.isSessionIdle(durationMs = 5_000L))
    }

    /**
     * Verifies session stops admitting new distinct domains after [RecordingSessionManager.MAX_SESSION_DOMAINS].
     */
    @Test
    fun respectsMaxDistinctDomainCapacity() {
        manager.startSession("com.example.busy")
        for (i in 0 until RecordingSessionManager.MAX_SESSION_DOMAINS) {
            assertTrue(manager.recordDomain("host$i.example.com"))
        }
        // Admitting another distinct host fails
        assertFalse(manager.recordDomain("overflow.example.com"))
        // Updating an already admitted host succeeds
        assertTrue(manager.recordDomain("host0.example.com"))
        assertEquals(RecordingSessionManager.MAX_SESSION_DOMAINS, manager.getRecordedDomains().size)
    }
}
