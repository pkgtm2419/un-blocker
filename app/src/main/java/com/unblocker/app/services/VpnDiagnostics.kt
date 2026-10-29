package com.unblocker.app.services

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-safe structured diagnostics and telemetry for VPN network events.
 *
 * CRITICAL PRIVACY INVARIANT:
 * Strictly zero PII, domains, URLs, client IPs, or packet payloads are recorded.
 * Only numeric failure codes, exception classes, queue depths, and resolver counts are recorded.
 */
object VpnDiagnostics {

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
        DATABASE_LOG_ERROR
    }

    data class DiagnosticEvent(
        val timestampMs: Long,
        val code: FailureCode,
        val exceptionClass: String?,
        val queueDepth: Int,
        val resolverCount: Int,
        val details: String? = null
    )

    private val _forwardingActive = AtomicLong(0)
    private val _forwardingRejected = AtomicLong(0)
    private val _upstreamTimeouts = AtomicLong(0)
    private val _packetProcessingErrors = AtomicLong(0)
    private val _tunReadErrors = AtomicLong(0)
    private val _tunWriteErrors = AtomicLong(0)

    val forwardingActiveCount: Long get() = _forwardingActive.get()
    val forwardingRejectedCount: Long get() = _forwardingRejected.get()
    val upstreamTimeoutCount: Long get() = _upstreamTimeouts.get()
    val packetProcessingErrorCount: Long get() = _packetProcessingErrors.get()
    val tunReadErrorCount: Long get() = _tunReadErrors.get()
    val tunWriteErrorCount: Long get() = _tunWriteErrors.get()

    private const val MAX_HISTORY_EVENTS = 200
    private val recentEvents = ConcurrentLinkedQueue<DiagnosticEvent>()

    fun record(
        code: FailureCode,
        exceptionClass: String? = null,
        queueDepth: Int = 0,
        resolverCount: Int = 0,
        details: String? = null
    ) {
        when (code) {
            FailureCode.FORWARDING_REJECTED -> _forwardingRejected.incrementAndGet()
            FailureCode.UPSTREAM_TIMEOUT -> _upstreamTimeouts.incrementAndGet()
            FailureCode.PACKET_PROCESSING, FailureCode.DNS_PARSE_ERROR -> _packetProcessingErrors.incrementAndGet()
            FailureCode.TUN_READ_ERROR -> _tunReadErrors.incrementAndGet()
            FailureCode.TUN_WRITE_ERROR -> _tunWriteErrors.incrementAndGet()
            else -> {}
        }

        val event = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            code = code,
            exceptionClass = exceptionClass,
            queueDepth = queueDepth,
            resolverCount = resolverCount,
            details = details
        )
        recentEvents.offer(event)
        while (recentEvents.size > MAX_HISTORY_EVENTS) {
            recentEvents.poll()
        }
    }

    fun forwardingActiveIncrement(): Long = _forwardingActive.incrementAndGet()
    fun forwardingActiveDecrement(): Long = _forwardingActive.decrementAndGet()

    fun forwardingRejected(queueDepth: Int = 0, resolverCount: Int = 0) {
        record(
            code = FailureCode.FORWARDING_REJECTED,
            exceptionClass = "java.util.concurrent.RejectedExecutionException",
            queueDepth = queueDepth,
            resolverCount = resolverCount
        )
    }

    fun getRecentEvents(): List<DiagnosticEvent> = recentEvents.toList()

    fun resetForTesting() {
        _forwardingActive.set(0)
        _forwardingRejected.set(0)
        _upstreamTimeouts.set(0)
        _packetProcessingErrors.set(0)
        _tunReadErrors.set(0)
        _tunWriteErrors.set(0)
        recentEvents.clear()
    }
}
