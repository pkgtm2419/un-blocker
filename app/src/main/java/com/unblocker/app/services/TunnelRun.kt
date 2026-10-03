package com.unblocker.app.services

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Resources belong to one run, including restarts of the same Android Service instance. */
internal class TunnelRun(val owner: Long, val startId: Int = 0) {
    val running = AtomicBoolean(true)
    val forwarding = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(64))
    private val cleanupStarted = AtomicBoolean(false)
    private val cleanupLock = Any()
    private val resources = ConcurrentHashMap.newKeySet<Closeable>()
    var descriptor: ParcelFileDescriptor? = null
    var thread: Thread? = null

    fun register(resource: Closeable): Boolean {
        synchronized(cleanupLock) {
            if (!running.get() || cleanupStarted.get()) {
                runCatching { resource.close() }
                return false
            }
            resources.add(resource)
            return true
        }
    }

    fun unregister(resource: Closeable): Boolean = resources.remove(resource)

    /** Keeps teardown from closing the TUN descriptor between a liveness check and packet write. */
    fun useWhileRunning(action: () -> Unit): Boolean = synchronized(cleanupLock) {
        if (!running.get() || cleanupStarted.get()) return@synchronized false
        action()
        true
    }

    fun close() {
        synchronized(cleanupLock) {
            if (!cleanupStarted.compareAndSet(false, true)) return
            running.set(false)
            forwarding.shutdownNow()
            resources.forEach { resource ->
                if (resources.remove(resource)) runCatching { resource.close() }
            }
            try { descriptor?.close() } catch (_: Exception) {}
            descriptor = null
            thread?.interrupt()
        }
    }
}
