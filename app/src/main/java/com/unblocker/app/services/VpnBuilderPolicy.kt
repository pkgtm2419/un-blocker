package com.unblocker.app.services

import android.net.VpnService

/**
 * Abstraction for applying application package routing rules to a VPN interface.
 */
interface VpnAppRoutingTarget {
    /**
     * Routes all network traffic from [packageName] through the VPN tunnel.
     */
    fun addAllowedApplication(packageName: String)

    /**
     * Bypasses the VPN tunnel for all network traffic from [packageName].
     */
    fun addDisallowedApplication(packageName: String)
}

/**
 * Application routing policy for [VpnService.Builder].
 *
 * Enforces strict mutual exclusivity: Android's [VpnService.Builder] throws an exception
 * if both allowed and disallowed applications are added to the same builder instance.
 *
 * During an isolated Learn session, only the target application is allowed.
 * In normal operation, Un-Blocker's own package (and user-configured exclusions) are disallowed.
 */
object VpnBuilderPolicy {
    /**
     * Applies mutually exclusive package routing to [target].
     *
     * @param target Receiver implementing application routing methods.
     * @param ownPackage Un-Blocker package name to exclude during normal operation.
     * @param isolatedPackage Target application package name during an isolated Learn session, or null.
     * @param excludedPackages Set of user-configured application package names to bypass during normal operation.
     */
    fun applyRouting(
        target: VpnAppRoutingTarget,
        ownPackage: String,
        isolatedPackage: String?,
        excludedPackages: Set<String> = emptySet(),
    ) {
        val trimmedIsolated = isolatedPackage?.trim()
        if (!trimmedIsolated.isNullOrEmpty()) {
            target.addAllowedApplication(trimmedIsolated)
        } else {
            if (ownPackage.isNotBlank()) {
                target.addDisallowedApplication(ownPackage.trim())
            }
            for (pkg in excludedPackages) {
                val clean = pkg.trim()
                if (clean.isNotEmpty() && clean != ownPackage.trim()) {
                    target.addDisallowedApplication(clean)
                }
            }
        }
    }

    /**
     * Adapts an Android [VpnService.Builder] to [VpnAppRoutingTarget] and applies routing rules.
     *
     * @param builder Target Android [VpnService.Builder].
     * @param ownPackage Un-Blocker package name to exclude during normal operation.
     * @param isolatedPackage Target application package name during an isolated Learn session, or null.
     * @param excludedPackages Set of user-configured application package names to bypass during normal operation.
     */
    fun applyToBuilder(
        builder: VpnService.Builder,
        ownPackage: String,
        isolatedPackage: String?,
        excludedPackages: Set<String> = emptySet(),
    ) {
        applyRouting(
            target =
                object : VpnAppRoutingTarget {
                    override fun addAllowedApplication(packageName: String) {
                        try {
                            builder.addAllowedApplication(packageName)
                        } catch (_: Exception) {
                        }
                    }

                    override fun addDisallowedApplication(packageName: String) {
                        try {
                            builder.addDisallowedApplication(packageName)
                        } catch (_: Exception) {
                        }
                    }
                },
            ownPackage = ownPackage,
            isolatedPackage = isolatedPackage,
            excludedPackages = excludedPackages,
        )
    }
}

/**
 * Determines tunnel recovery actions when an isolated Learn session finishes, times out,
 * or leaves persisted isolation flags behind after a crash or process death.
 */
object IsolatedSessionRecoveryPolicy {
    /**
     * Recovery action required at startup or session termination.
     */
    enum class RecoveryAction {
        /** No isolated session was active. */
        NONE,

        /** Clear isolated state and restore normal device-wide filtering because user protection is enabled. */
        RESTORE_NORMAL_TUNNEL,

        /** Clear isolated state and tear down VPN because user protection was not enabled before learning. */
        STOP_TUNNEL,
    }

    /**
     * Computes which recovery step is needed for an isolated Learn session state.
     *
     * @param isolatedSessionWasActive Whether persisted flags show an isolated session was active.
     * @param protectionEnabled Whether full-device protection was requested by the user.
     * @return Required recovery action.
     */
    fun determineRecoveryAction(
        isolatedSessionWasActive: Boolean,
        protectionEnabled: Boolean,
    ): RecoveryAction {
        if (!isolatedSessionWasActive) return RecoveryAction.NONE
        return if (protectionEnabled) RecoveryAction.RESTORE_NORMAL_TUNNEL else RecoveryAction.STOP_TUNNEL
    }

    /**
     * Performs startup crash/process-death recovery when an isolated session was left marked active.
     *
     * @param isolatedSessionWasActive Whether persisted preferences indicate an interrupted isolated session.
     * @param protectionEnabled Whether normal protection intent is enabled.
     * @param clearIsolatedState Callback clearing persisted isolated-session flags.
     * @param stopRecordingSession Callback resetting any active session coordinator state.
     * @param stopService Callback stopping the service when user protection is not enabled.
     * @return Executed [RecoveryAction].
     */
    fun recoverOnStartup(
        isolatedSessionWasActive: Boolean,
        protectionEnabled: Boolean,
        clearIsolatedState: () -> Unit,
        stopRecordingSession: () -> Unit,
        stopService: () -> Unit,
    ): RecoveryAction {
        val action = determineRecoveryAction(isolatedSessionWasActive, protectionEnabled)
        if (action != RecoveryAction.NONE) {
            clearIsolatedState()
            stopRecordingSession()
            if (action == RecoveryAction.STOP_TUNNEL) {
                stopService()
            }
        }
        return action
    }
}
