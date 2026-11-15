package com.unblocker.app.services

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded process-local diagnostic events and counters for VPN operations.
 *
 * Callers must supply only non-sensitive codes, class names and operational details. String fields are
 * retained as supplied and are not sanitized here; domains, addresses and payloads must not be passed.
 */
object VpnDiagnostics {
    /**
     * Operational event categories used for local diagnostics and selected counters.
     */
    enum class FailureCode {
        PACKET_PROCESSING,
        FORWARDING_REJECTED,
        UPSTREAM_TIMEOUT,
        VPN_START,
        VPN_STOP,
        TUN_READ_ERROR,
        TUN_WRITE_ERROR,
        DNS_PARSE_ERROR,
        VPN_LOOP_FAILURE,
        DATABASE_LOG_ERROR,
    }

    /**
     * Local diagnostic metadata supplied by VPN components.
     *
     * @property timestampMs Event time in Unix epoch milliseconds.
     * @property code Operational failure category.
     * @property exceptionClass Exception class or non-sensitive diagnostic label, when available.
     * @property queueDepth Forwarding/write queue depth in queued items.
     * @property resolverCount Number of configured resolver candidates.
     * @property details Optional non-sensitive operational description supplied by the caller.
     */
    data class DiagnosticEvent(
        val timestampMs: Long,
        val code: FailureCode,
        val exceptionClass: String?,
        val queueDepth: Int,
        val resolverCount: Int,
        val details: String? = null,
    )

    private val _forwardingActive = AtomicLong(0)
    private val _forwardingRejected = AtomicLong(0)
    private val _upstreamTimeouts = AtomicLong(0)
    private val _packetProcessingErrors = AtomicLong(0)
    private val _tunReadErrors = AtomicLong(0)
    private val _tunWriteErrors = AtomicLong(0)
    private val _coalescedQueries = AtomicLong(0)

    /**
     * Current count of forwarding operations tracked by increment/decrement calls.
     */
    val forwardingActiveCount: Long get() = _forwardingActive.get()

    /**
     * Number of recorded executor forwarding rejections.
     */
    val forwardingRejectedCount: Long get() = _forwardingRejected.get()

    /**
     * Number of recorded upstream failure/timeout events.
     */
    val upstreamTimeoutCount: Long get() = _upstreamTimeouts.get()

    /**
     * Number of recorded packet-processing and DNS-parse error events.
     */
    val packetProcessingErrorCount: Long get() = _packetProcessingErrors.get()

    /**
     * Number of recorded TUN read-error events.
     */
    val tunReadErrorCount: Long get() = _tunReadErrors.get()

    /**
     * Number of recorded TUN write-error events, including queue backpressure drops.
     */
    val tunWriteErrorCount: Long get() = _tunWriteErrors.get()

    /**
     * Number of duplicate queries attached to an existing resolution.
     */
    val coalescedQueryCount: Long get() = _coalescedQueries.get()

    private const val MAX_HISTORY_EVENTS = 200
    private val recentEvents = ConcurrentLinkedQueue<DiagnosticEvent>()

    /**
     * Records one local event, updates its category counter and trims retained history.
     *
     * @param code Operational event category.
     * @param exceptionClass Non-sensitive exception class or diagnostic label.
     * @param queueDepth Queue size in items, when relevant.
     * @param resolverCount Number of configured resolver candidates.
     * @param details Non-sensitive operational description; callers must exclude private traffic data.
     */
    fun record(
        code: FailureCode,
        exceptionClass: String? = null,
        queueDepth: Int = 0,
        resolverCount: Int = 0,
        details: String? = null,
    ) {
        when (code) {
            FailureCode.FORWARDING_REJECTED -> {
                _forwardingRejected.incrementAndGet()
            }

            FailureCode.UPSTREAM_TIMEOUT -> {
                _upstreamTimeouts.incrementAndGet()
            }

            FailureCode.PACKET_PROCESSING, FailureCode.DNS_PARSE_ERROR -> {
                _packetProcessingErrors.incrementAndGet()
            }

            FailureCode.TUN_READ_ERROR -> {
                _tunReadErrors.incrementAndGet()
            }

            FailureCode.TUN_WRITE_ERROR -> {
                _tunWriteErrors.incrementAndGet()
            }

            else -> {}
        }

        val event =
            DiagnosticEvent(
                timestampMs = System.currentTimeMillis(),
                code = code,
                exceptionClass = exceptionClass,
                queueDepth = queueDepth,
                resolverCount = resolverCount,
                details = details,
            )
        recentEvents.offer(event)
        while (recentEvents.size > MAX_HISTORY_EVENTS) {
            recentEvents.poll()
        }
    }

    /**
     * Increments active forwarding operations and returns the new count.
     */
    fun forwardingActiveIncrement(): Long = _forwardingActive.incrementAndGet()

    /**
     * Decrements active forwarding operations and returns the new count.
     */
    fun forwardingActiveDecrement(): Long = _forwardingActive.decrementAndGet()

    /**
     * Increments the duplicate-query counter and returns the new count.
     */
    fun recordCoalesced(): Long = _coalescedQueries.incrementAndGet()

    /**
     * Records executor rejection with a fixed exception-class label.
     *
     * @param queueDepth Forwarding queue size in items.
     * @param resolverCount Number of configured resolver candidates.
     */
    fun forwardingRejected(
        queueDepth: Int = 0,
        resolverCount: Int = 0,
    ) {
        record(
            code = FailureCode.FORWARDING_REJECTED,
            exceptionClass = "java.util.concurrent.RejectedExecutionException",
            queueDepth = queueDepth,
            resolverCount = resolverCount,
        )
    }

    /**
     * Returns the currently retained local diagnostic events in queue order.
     */
    fun getRecentEvents(): List<DiagnosticEvent> = recentEvents.toList()

    /**
     * Resets all counters and clears local event history for tests.
     */
    fun resetForTesting() {
        _forwardingActive.set(0)
        _forwardingRejected.set(0)
        _upstreamTimeouts.set(0)
        _packetProcessingErrors.set(0)
        _tunReadErrors.set(0)
        _tunWriteErrors.set(0)
        _coalescedQueries.set(0)
        recentEvents.clear()
    }
}
