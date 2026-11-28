package com.unblocker.app.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Published VPN lifecycle phases, distinct from stored protection intent.
 */
enum class ServiceStatus { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

/**
 * Tracks VPN lifecycle and generation ownership across service runs.
 *
 * @param onStatusChanged Optional callback invoked under the session monitor after status publication.
 */
class VpnSessionState(
    private val onStatusChanged: ((ServiceStatus) -> Unit)? = null,
) {
    private val mutableStatus = MutableStateFlow(ServiceStatus.STOPPED)

    /**
     * Read-only published lifecycle status.
     */
    val status = mutableStatus.asStateFlow()
    private val mutableActive = MutableStateFlow(false)

    /**
     * Read-only established-tunnel activity; false during startup, teardown and errors.
     */
    val active = mutableActive.asStateFlow()
    private var generation = 0L

    /**
     * Advances the owner generation, publishes STARTING and returns the new owner token.
     */
    @Synchronized fun begin(): Long {
        generation++
        setStatus(ServiceStatus.STARTING, false)
        return generation
    }
    /**
     * Checks ownership against the latest generation.
     *
     * @param owner Token returned by begin.
     * @return Whether the token is current, independently of lifecycle status.
     */

    /**
     * Publishes RUNNING only for the current owner while STARTING.
     *
     * @param owner Token returned by begin.
     * @return Whether the transition succeeded.
     */
    @Synchronized fun owns(owner: Long) = owner == generation

    /**
     * Publishes ERROR only when the owner token is current.
     *
     * @param owner Token returned by begin.
     */
    @Synchronized fun established(owner: Long): Boolean = owns(owner) && established()

    /**
     * Publishes STOPPING only when the owner token is current.
     *
     * @param owner Token returned by begin.
     */
    @Synchronized fun failed(owner: Long) {
        if (owns(owner)) failed()
    }

    /**
     * Publishes STOPPED only when the owner token is current.
     *
     * @param owner Token returned by begin.
     */
    @Synchronized fun stopping(owner: Long) {
        if (owns(owner)) stopping()
    }

    @Synchronized fun stop(owner: Long) {
        if (owns(owner)) stop()
    }

    @Synchronized private fun established(): Boolean {
        if (mutableStatus.value != ServiceStatus.STARTING) return false
        setStatus(ServiceStatus.RUNNING, true)
        return true
    }

    /**
     * Publishes ERROR and clears active state without changing the owner generation.
     */
    @Synchronized fun failed() {
        setStatus(ServiceStatus.ERROR, false)
    }

    /**
     * Publishes STOPPING and clears active state without changing the owner generation.
     */
    @Synchronized fun stopping() {
        setStatus(ServiceStatus.STOPPING, false)
    }

    /**
     * Publishes STOPPED and clears active state without changing the owner generation.
     */
    @Synchronized fun stop() {
        setStatus(ServiceStatus.STOPPED, false)
    }

    private fun setStatus(
        status: ServiceStatus,
        active: Boolean,
    ) {
        mutableActive.value = active
        mutableStatus.value = status
        onStatusChanged?.invoke(status)
    }
}

/**
 * Eligibility gate for restarting protection after a boot or package-replacement broadcast.
 */
object BootPolicy {
    /** System broadcast sent after the device finishes booting. */
    const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"

    /** OEM quick-boot broadcast action used on supported devices. */
    const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

    /** System broadcast delivered to an updated package after replacement finishes. */
    const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    /**
     * Checks whether [action] should attempt a background VPN protection restart.
     *
     * @param action Broadcast intent action, or null.
     * @return True for boot, quick-boot, and package-replacement broadcasts.
     */
    fun isEligibleAction(action: String?): Boolean =
        action == ACTION_BOOT_COMPLETED ||
            action == ACTION_QUICKBOOT_POWERON ||
            action == ACTION_MY_PACKAGE_REPLACED

    /**
     * Requires restart intent, protection intent and existing Android VPN consent.
     *
     * @param autoRestart Stored auto-restart-on-boot preference.
     * @param protectionEnabled Stored protection-enabled preference.
     * @param permissionGranted Whether VPN preparation requires no consent activity.
     * @return Whether all three prerequisites are true.
     */
    fun shouldStart(
        autoRestart: Boolean,
        protectionEnabled: Boolean,
        permissionGranted: Boolean,
    ) = autoRestart && protectionEnabled && permissionGranted
}
