package com.unblocker.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable coroutine dispatchers; tests can supply controlled dispatchers.
 *
 * @property main Dispatcher for Android main-thread work.
 * @property io Dispatcher for blocking I/O.
 * @property default Dispatcher for CPU-bound work.
 */
data class CoroutineDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)
