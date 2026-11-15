package com.unblocker.vpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable state for callers of the separate library VPN engine.
 *
 * The primary StateFlow retains the latest transition. This holder alone does not establish
 * that a tunnel exists or that all device DNS traffic is covered.
 */
object VpnStateHolder {

    /**
     * Lifecycle states that library engine callers can publish.
     */
    enum class State {
        /** No library engine operation is reported. */
        IDLE,
        /** A library engine startup is reported in progress. */
        STARTING,
        /** The caller reports the library engine running. */
        RUNNING,
        /** A library engine shutdown is reported in progress. */
        STOPPING,
        /** The caller reports a library engine error. */
        ERROR
    }

    private val mutableState = MutableStateFlow(State.IDLE)

    /**
     * Read-only stream of the most recently published library engine state.
     */
    val state: StateFlow<State> = mutableState.asStateFlow()

    /**
     * Snapshot flow initialized from the current state when this property is read.
     *
     * Each access creates a new flow that is not updated by later transitions. Observe [state]
     * and derive RUNNING status when ongoing updates are required.
     */
    val isActive: StateFlow<Boolean>
        get() = MutableStateFlow(mutableState.value == State.RUNNING).also {
            // Retain the existing snapshot semantics; this flow has no transition collector.
        }

    internal fun transitionTo(newState: State) {
        mutableState.value = newState
    }

    /**
     * Whether the current library state snapshot equals [State.RUNNING].
     */
    val isRunning: Boolean get() = mutableState.value == State.RUNNING
}
