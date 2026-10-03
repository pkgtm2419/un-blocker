package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckPolicyTest {
    @Test fun healthWorkRunsOnlyWhileProtectionIsDesired() {
        assertTrue(HealthCheckPolicy.shouldRun(protectionEnabled = true))
        assertFalse(HealthCheckPolicy.shouldRun(protectionEnabled = false))
    }

    @Test fun defaultCadenceIsSixHoursAndConfigHasNoFakeLoggingOrDownloads() {
        assertEquals(360, HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
        val config = java.io.File("src/main/assets/default_config.json").readText()
        assertFalse(config.contains("connectionLoggingEnabled"))
        assertFalse(config.contains("logRetentionDays"))
        assertFalse(config.contains("updateAdListFrequency"))
        assertFalse(config.contains("updateAdultListFrequency"))
    }
}
