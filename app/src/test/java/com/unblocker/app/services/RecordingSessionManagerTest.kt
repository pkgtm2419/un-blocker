package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingSessionManagerTest {

    private lateinit var manager: RecordingSessionManager

    @Before
    fun setup() {
        manager = RecordingSessionManager.instance
        manager.stopSession()
    }

    @Test
    fun testStartSessionAndRecordDomain() {
        assertFalse(manager.isRecordingActive())
        val session = manager.startSession("com.example.app", 1042)
        assertTrue(manager.isRecordingActive())
        assertEquals("com.example.app", session.targetPackage)
        assertEquals(1042, session.targetUid)

        // Record for matching UID
        val recordedMatching = manager.recordDomain("api.example.com", 1042)
        assertTrue(recordedMatching)

        // Record for another UID should be rejected
        val recordedOther = manager.recordDomain("other.com", 9999)
        assertFalse(recordedOther)

        val domains = manager.getRecordedDomains()
        assertEquals(1, domains.size)
        assertEquals("api.example.com", domains[0].domain)
        assertEquals(1, domains[0].count)

        // Record same domain again to verify count increment
        manager.recordDomain("api.example.com", 1042)
        val updated = manager.getRecordedDomains()
        assertEquals(1, updated.size)
        assertEquals(2, updated[0].count)

        // Stop session
        val summary = manager.stopSession()
        assertFalse(manager.isRecordingActive())
        assertEquals(1, summary.size)
        assertEquals("api.example.com", summary[0].domain)
    }

    @Test
    fun testSessionAutoExpiry() {
        // Start a session with 1ms max duration
        manager.startSession("com.example.short", 1001, maxDurationMs = 1L)
        Thread.sleep(10)
        assertFalse(manager.isRecordingActive())
        assertNull(manager.activeSession.value)
    }
}
