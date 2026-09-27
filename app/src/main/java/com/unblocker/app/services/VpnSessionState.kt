package com.unblocker.app.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceStatus { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

class VpnSessionState {
    private val mutableStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val status = mutableStatus.asStateFlow()
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    private var generation = 0L

    @Synchronized fun begin(): Long {
        generation++
        mutableStatus.value = ServiceStatus.STARTING
        mutableActive.value = false
        return generation
    }
    @Synchronized fun owns(owner: Long) = owner == generation
    @Synchronized fun established(owner: Long): Boolean = owns(owner) && established()
    @Synchronized fun failed(owner: Long) { if (owns(owner)) failed() }
    @Synchronized fun stopping(owner: Long) { if (owns(owner)) stopping() }
    @Synchronized fun stop(owner: Long) { if (owns(owner)) stop() }

    @Synchronized private fun established(): Boolean {
        if (mutableStatus.value != ServiceStatus.STARTING) return false
        mutableActive.value = true
        mutableStatus.value = ServiceStatus.RUNNING
        return true
    }
    @Synchronized fun failed() {
        mutableActive.value = false
        mutableStatus.value = ServiceStatus.ERROR
    }
    @Synchronized fun stopping() {
        mutableActive.value = false
        mutableStatus.value = ServiceStatus.STOPPING
    }
    @Synchronized fun stop() {
        mutableActive.value = false
        mutableStatus.value = ServiceStatus.STOPPED
    }
}

object BootPolicy {
    fun shouldStart(autoRestart: Boolean, protectionEnabled: Boolean, permissionGranted: Boolean) =
        autoRestart && protectionEnabled && permissionGranted
}
