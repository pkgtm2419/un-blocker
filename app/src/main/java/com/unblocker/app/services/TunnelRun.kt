package com.unblocker.app.services

import android.os.ParcelFileDescriptor
import java.net.DatagramSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Resources belong to one run, including restarts of the same Android Service instance. */
internal class TunnelRun(val owner: Long, val startId: Int = 0) {
    val running = AtomicBoolean(true)
    val forwarding = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(64))
    val sockets = ConcurrentHashMap.newKeySet<DatagramSocket>()
    var descriptor: ParcelFileDescriptor? = null
    var thread: Thread? = null

    fun close() {
        running.set(false)
        forwarding.shutdownNow()
        sockets.forEach { it.close() }
        try { descriptor?.close() } catch (_: Exception) {}
        descriptor = null
        thread?.interrupt()
    }
}
