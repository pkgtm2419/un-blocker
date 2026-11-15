package com.unblocker.app.services

import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes TUN packet writes through a bounded queue and one daemon writer thread.
 *
 * @param run Tunnel run whose liveness is checked before enqueue and write.
 * @param session Session whose generation ownership is checked before enqueue and write.
 * @param outputStream TUN output; null skips I/O while still consuming queued packets.
 * @property queueCapacity Maximum queued packet arrays.
 * @param onWriteError Callback for write exceptions.
 * @throws IllegalArgumentException If queueCapacity is not positive.
 */
internal class TunResponseWriter(
    private val run: TunnelRun,
    private val session: VpnSessionState,
    private val outputStream: OutputStream?,
    val queueCapacity: Int = 256,
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

    /**
     * Number of packet arrays rejected because the queue was full.
     */
    val droppedPackets: Long get() = _droppedPackets.get()
    /**
     * Number of dequeued packets consumed successfully, including when outputStream is null.
     */
    val writtenPackets: Long get() = _writtenPackets.get()
    /**
     * Current number of queued packet arrays.
     */
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
     * Attempts a nonblocking packet enqueue for a live owned session.
     *
     * @param packet Complete reply array whose ownership transfers to the writer when accepted.
     * @return Whether queued; a full queue increments the drop counter, while dead-session rejection does not.
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

    /**
     * Stops new writes, clears the queue, interrupts the writer and waits up to 250 milliseconds.
     */
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
