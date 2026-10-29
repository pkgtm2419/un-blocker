package com.unblocker.app.services

import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated single-writer queue for serializing all packet writes to the TUN interface.
 *
 * Eliminates monitor lock contention across forwarding workers, cache hits, blocked responses,
 * and TCP responders. Enforces run-liveness and session-ownership invariants on output.
 */
internal class TunResponseWriter(
    private val run: TunnelRun,
    private val session: VpnSessionState,
    private val outputStream: OutputStream?,
    val queueCapacity: Int = 128,
    private val onWriteError: (Exception) -> Unit = { e ->
        VpnDiagnostics.record(
            code = VpnDiagnostics.FailureCode.TUN_WRITE_ERROR,
            exceptionClass = e::class.java.name
        )
    }
) : Closeable {

    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacity)
    private val stopped = AtomicBoolean(false)
    private val _droppedPackets = AtomicLong(0)
    private val _writtenPackets = AtomicLong(0)

    val droppedPackets: Long get() = _droppedPackets.get()
    val writtenPackets: Long get() = _writtenPackets.get()
    val queueSize: Int get() = queue.size

    private val writerThread = Thread({
        while (!stopped.get() && run.running.get()) {
            try {
                val packet = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (!run.running.get() || !session.owns(run.owner) || !session.active.value) {
                    break
                }
                outputStream?.write(packet)
                _writtenPackets.incrementAndGet()
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                onWriteError(e)
            }
        }
    }, "TunResponseWriter-${run.owner}").apply {
        isDaemon = true
        start()
    }

    /**
     * Non-blocking packet enqueue from any thread.
     * Returns true if queued, false if rejected due to queue full or dead session.
     */
    fun offer(packet: ByteArray): Boolean {
        if (stopped.get() || !run.running.get() || !session.owns(run.owner) || !session.active.value) {
            return false
        }
        val accepted = queue.offer(packet)
        if (!accepted) {
            _droppedPackets.incrementAndGet()
            VpnDiagnostics.record(
                code = VpnDiagnostics.FailureCode.TUN_WRITE_ERROR,
                exceptionClass = "QueueBackpressureDrop",
                queueDepth = queue.size
            )
        }
        return accepted
    }

    override fun close() {
        if (stopped.compareAndSet(false, true)) {
            queue.clear()
            writerThread.interrupt()
            try {
                writerThread.join(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
