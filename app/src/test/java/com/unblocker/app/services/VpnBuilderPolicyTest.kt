package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies mutual exclusivity in [VpnBuilderPolicy] and startup/crash recovery in [IsolatedSessionRecoveryPolicy] (WP-02).
 */
class VpnBuilderPolicyTest {
    private class FakeRoutingTarget : VpnAppRoutingTarget {
        val allowed = mutableListOf<String>()
        val disallowed = mutableListOf<String>()

        override fun addAllowedApplication(packageName: String) {
            check(disallowed.isEmpty()) {
                "Android VpnService.Builder forbids mixing allowed and disallowed applications"
            }
            allowed.add(packageName)
        }

        override fun addDisallowedApplication(packageName: String) {
            check(allowed.isEmpty()) {
                "Android VpnService.Builder forbids mixing allowed and disallowed applications"
            }
            disallowed.add(packageName)
        }
    }

    @Test
    fun normalModeDisallowsOwnAndExcludedPackagesWithoutAllowedList() {
        val target = FakeRoutingTarget()
        VpnBuilderPolicy.applyRouting(
            target = target,
            ownPackage = "com.unblocker.app",
            isolatedPackage = null,
            excludedPackages = setOf("com.bank.app", "com.lan.printer", "com.unblocker.app", " "),
        )

        assertTrue(target.allowed.isEmpty())
        assertEquals(listOf("com.unblocker.app", "com.bank.app", "com.lan.printer"), target.disallowed)
    }

    @Test
    fun isolatedModeAllowsOnlyTargetPackageAndNeverAddsDisallowedPackages() {
        val target = FakeRoutingTarget()
        VpnBuilderPolicy.applyRouting(
            target = target,
            ownPackage = "com.unblocker.app",
            isolatedPackage = "com.target.app",
            excludedPackages = setOf("com.bank.app"),
        )

        assertEquals(listOf("com.target.app"), target.allowed)
        assertTrue("Disallowed list must stay empty when isolatedPackage is set", target.disallowed.isEmpty())
    }

    @Test
    fun blankIsolatedPackageFallsBackToNormalRouting() {
        val target = FakeRoutingTarget()
        VpnBuilderPolicy.applyRouting(
            target = target,
            ownPackage = "com.unblocker.app",
            isolatedPackage = "   ",
            excludedPackages = emptySet(),
        )

        assertTrue(target.allowed.isEmpty())
        assertEquals(listOf("com.unblocker.app"), target.disallowed)
    }

    @Test
    fun recoveryOnStartupNoOpWhenNoIsolatedSessionPersisted() {
        var cleared = false
        var stoppedSession = false
        var stoppedService = false

        val action =
            IsolatedSessionRecoveryPolicy.recoverOnStartup(
                isolatedSessionWasActive = false,
                protectionEnabled = true,
                clearIsolatedState = { cleared = true },
                stopRecordingSession = { stoppedSession = true },
                stopService = { stoppedService = true },
            )

        assertEquals(IsolatedSessionRecoveryPolicy.RecoveryAction.NONE, action)
        assertFalse(cleared)
        assertFalse(stoppedSession)
        assertFalse(stoppedService)
    }

    @Test
    fun recoveryOnStartupRestoresNormalTunnelWhenProtectionWasEnabled() {
        var cleared = false
        var stoppedSession = false
        var stoppedService = false

        val action =
            IsolatedSessionRecoveryPolicy.recoverOnStartup(
                isolatedSessionWasActive = true,
                protectionEnabled = true,
                clearIsolatedState = { cleared = true },
                stopRecordingSession = { stoppedSession = true },
                stopService = { stoppedService = true },
            )

        assertEquals(IsolatedSessionRecoveryPolicy.RecoveryAction.RESTORE_NORMAL_TUNNEL, action)
        assertTrue(cleared)
        assertTrue(stoppedSession)
        assertFalse(stoppedService)
    }

    @Test
    fun recoveryOnStartupStopsTunnelWhenProtectionWasDisabled() {
        var cleared = false
        var stoppedSession = false
        var stoppedService = false

        val action =
            IsolatedSessionRecoveryPolicy.recoverOnStartup(
                isolatedSessionWasActive = true,
                protectionEnabled = false,
                clearIsolatedState = { cleared = true },
                stopRecordingSession = { stoppedSession = true },
                stopService = { stoppedService = true },
            )

        assertEquals(IsolatedSessionRecoveryPolicy.RecoveryAction.STOP_TUNNEL, action)
        assertTrue(cleared)
        assertTrue(stoppedSession)
        assertTrue(stoppedService)
    }
}
