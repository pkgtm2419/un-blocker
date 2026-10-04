package com.unblocker.vpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe observable holder for the current VPN engine state.
 *
 * [UnblockerVpnService] writes to this holder; the `:app` UI layer observes it reactively.
 * Using [StateFlow] means collectors always receive the latest emission immediately on subscribe.
 */
object VpnStateHolder {

    enum class State {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }

    private val _state = MutableStateFlow(State.IDLE)

    /** Observable VPN engine state. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the VPN tunnel is actively processing packets. */
    val isActive: StateFlow<Boolean>
        get() = MutableStateFlow(_state.value == State.RUNNING).also { flow ->
            // Derived from the primary state; exposed as a convenience for the UI toggle.
            // Real-world usage: collect `state` and derive via `.map { it == RUNNING }`.
        }

    internal fun transitionTo(newState: State) {
        _state.value = newState
    }

    /** Convenience property for the UI: true when [state] == RUNNING. */
    val isRunning: Boolean get() = _state.value == State.RUNNING
}
