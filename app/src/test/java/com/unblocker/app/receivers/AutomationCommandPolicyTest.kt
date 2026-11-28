package com.unblocker.app.receivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies opt-in toggle enforcement and constant-time token validation in [AutomationCommandPolicy] (WP-33 / F37).
 */
class AutomationCommandPolicyTest {
    @Test
    fun disabledAutomationRejectsEvenWithMatchingToken() {
        val result =
            AutomationCommandPolicy.authorize(
                automationEnabled = false,
                storedToken = "0123456789abcdef",
                suppliedToken = "0123456789abcdef",
                action = AutomationCommandPolicy.ACTION_ENABLE,
            )
        assertNull(result)
    }

    @Test
    fun mismatchedOrBlankTokenIsRejected() {
        assertNull(
            AutomationCommandPolicy.authorize(
                automationEnabled = true,
                storedToken = "0123456789abcdef",
                suppliedToken = "0123456789abcdee",
                action = AutomationCommandPolicy.ACTION_DISABLE,
            ),
        )
        assertNull(
            AutomationCommandPolicy.authorize(
                automationEnabled = true,
                storedToken = "",
                suppliedToken = "",
                action = AutomationCommandPolicy.ACTION_PAUSE,
            ),
        )
    }

    @Test
    fun enabledAndValidTokenMapsAllSupportedActions() {
        val token = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertEquals(
            AutomationCommand.ENABLE,
            AutomationCommandPolicy.authorize(true, token, token, AutomationCommandPolicy.ACTION_ENABLE),
        )
        assertEquals(
            AutomationCommand.DISABLE,
            AutomationCommandPolicy.authorize(true, token, token, AutomationCommandPolicy.ACTION_DISABLE),
        )
        assertEquals(
            AutomationCommand.PAUSE,
            AutomationCommandPolicy.authorize(true, token, token, AutomationCommandPolicy.ACTION_PAUSE),
        )
        assertEquals(
            AutomationCommand.RESUME,
            AutomationCommandPolicy.authorize(true, token, token, AutomationCommandPolicy.ACTION_RESUME),
        )
        assertNull(
            AutomationCommandPolicy.authorize(true, token, token, "com.unblocker.app.UNKNOWN"),
        )
    }
}
