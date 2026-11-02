package com.unblocker.app.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceStatus { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

class VpnSessionState(private val onStatusChanged: ((ServiceStatus) -> Unit)? = null) {
    private val mutableStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val status = mutableStatus.asStateFlow()
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    private var generation = 0L

    @Synchronized fun begin(): Long {
        generation++
        setStatus(ServiceStatus.STARTING, false)
        return generation
    }
    @Synchronized fun owns(owner: Long) = owner == generation
    @Synchronized fun established(owner: Long): Boolean = owns(owner) && established()
    @Synchronized fun failed(owner: Long) { if (owns(owner)) failed() }
    @Synchronized fun stopping(owner: Long) { if (owns(owner)) stopping() }
    @Synchronized fun stop(owner: Long) { if (owns(owner)) stop() }

    @Synchronized private fun established(): Boolean {
        if (mutableStatus.value != ServiceStatus.STARTING) return false
        setStatus(ServiceStatus.RUNNING, true)
        return true
    }
    @Synchronized fun failed() {
        setStatus(ServiceStatus.ERROR, false)
    }
    @Synchronized fun stopping() {
        setStatus(ServiceStatus.STOPPING, false)
    }
    @Synchronized fun stop() {
        setStatus(ServiceStatus.STOPPED, false)
    }

    private fun setStatus(status: ServiceStatus, active: Boolean) {
        mutableActive.value = active
        mutableStatus.value = status
        onStatusChanged?.invoke(status)
    }
}

object BootPolicy {
    fun shouldStart(autoRestart: Boolean, protectionEnabled: Boolean, permissionGranted: Boolean) =
        autoRestart && protectionEnabled && permissionGranted
}
