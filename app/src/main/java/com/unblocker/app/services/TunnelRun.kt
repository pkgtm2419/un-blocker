package com.unblocker.app.services

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns resources for one VPN generation, including restarts of a service instance.
 *
 * @property owner Session generation token.
 * @property startId Android service-start identifier associated with this run.
 * @param coreThreads Core forwarding worker count.
 * @param maxThreads Maximum forwarding worker count.
 * @param queueCapacity Maximum queued forwarding tasks.
 */
internal class TunnelRun(
    val owner: Long,
    val startId: Int = 0,
    coreThreads: Int = DEFAULT_CORE_POOL_SIZE,
    maxThreads: Int = DEFAULT_MAX_POOL_SIZE,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
) {
    /**
     * CPU-dependent forwarding pool bounds and idle timeout.
     */
    companion object {
        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()

        /**
         * Default core worker count derived from CPU count and clamped to 4 through 8.
         * DNS forwarding is I/O-bound and coalesced; a large pool wastes RAM and scheduler wakeups.
         */
        val DEFAULT_CORE_POOL_SIZE = maxOf(4, CPU_COUNT).coerceAtMost(8)

        /**
         * Default maximum worker count, twice the core count and at most 16.
         */
        val DEFAULT_MAX_POOL_SIZE = (DEFAULT_CORE_POOL_SIZE * 2).coerceAtMost(16)

        /**
         * Default maximum number of queued forwarding tasks.
         */
        const val DEFAULT_QUEUE_CAPACITY = 256

        /**
         * Idle worker timeout in seconds, also applied to core threads.
         */
        const val KEEP_ALIVE_SECONDS = 60L
    }

    /**
     * Atomic run-liveness flag cleared during cleanup.
     */
    val running = AtomicBoolean(true)

    /**
     * Bounded executor owning blocking upstream resolution work.
     */
    val forwarding: ThreadPoolExecutor =
        ThreadPoolExecutor(
            coreThreads,
            maxThreads,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(queueCapacity),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    private val cleanupStarted = AtomicBoolean(false)
    private val cleanupLock = Any()
    private val resources = ConcurrentHashMap.newKeySet<Closeable>()

    /**
     * TUN descriptor owned by this run and cleared during close.
     */
    var descriptor: ParcelFileDescriptor? = null

    /**
     * Packet-reader thread interrupted during run cleanup.
     */
    var thread: Thread? = null

    /**
     * Registers a closeable resource unless cleanup has begun.
     *
     * @param resource Resource whose ownership transfers to this run when accepted.
     * @return Whether registration succeeded; rejected resources are closed immediately.
     */
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

    /**
     * Removes cleanup ownership for a resource.
     *
     * @param resource Registered resource to detach.
     * @return Whether this run owned the resource; caller can close it when true.
     */
    fun unregister(resource: Closeable): Boolean = resources.remove(resource)

    /**
     * Excludes cleanup while checking liveness and executing an operation.
     *
     * @param action Operation run while the cleanup monitor is held.
     * @return Whether action ran; action exceptions propagate.
     */
    fun useWhileRunning(action: () -> Unit): Boolean =
        synchronized(cleanupLock) {
            if (!running.get() || cleanupStarted.get()) return@synchronized false
            action()
            true
        }

    /**
     * Idempotently marks the run stopped, interrupts workers/reader and closes owned resources.
     */
    fun close() {
        synchronized(cleanupLock) {
            if (!cleanupStarted.compareAndSet(false, true)) return
            running.set(false)
            forwarding.shutdownNow()
            resources.forEach { resource ->
                if (resources.remove(resource)) runCatching { resource.close() }
            }
            try {
                descriptor?.close()
            } catch (_: Exception) {
            }
            descriptor = null
            thread?.interrupt()
        }
    }
}
