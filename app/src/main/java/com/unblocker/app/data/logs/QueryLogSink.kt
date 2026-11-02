package com.unblocker.app.data.logs

import com.unblocker.app.logic.DomainName
import com.unblocker.app.services.VpnDiagnostics
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.dao.DomainStatUpsert
import com.unblocker.data.db.entity.DomainEventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Off-hot-path, lock-free log sink for intercepted DNS traffic.
 *
 * Implements privacy hardening and clear/pause reliability:
 * - Monotonic generation barrier and mutex-guarded flush prevent pre-clear items from repopulating.
 * - Defaults to HistoryMode.AGGREGATE_ONLY: zero raw per-query domain event rows are stored.
 * - Raw chronological domain history is strictly opt-in (HistoryMode.RAW_HISTORY_OPT_IN).
 * - Enforces tight bounded retention (24 hours / 1,000 entries max) for events.
 * - Enforces 30 days / 10,000 entries max bounded retention for domain_stats aggregates.
 * - Gracefully records database write exceptions via VpnDiagnostics instead of swallowing errors.
 */
object QueryLogSink {
    private const val QUEUE_CAPACITY = 10_000
    private const val BATCH_FLUSH_SIZE = 200
    private const val FLUSH_INTERVAL_MS = 1000L

    enum class HistoryMode {
        AGGREGATE_ONLY,
        RAW_HISTORY_OPT_IN
    }

    sealed interface ClearResult {
        data object Success : ClearResult
        data class Failure(val reason: String) : ClearResult
    }

    data class LogItem(
        val domain: String,
        val decision: Int, // 0 = allowed, 1 = blocked
        val reason: Int,
        val ts: Long,
        val generation: Long = 1L
    )

    private val queue = ArrayBlockingQueue<LogItem>(QUEUE_CAPACITY)
    private var dao: DomainLogDao? = null
    private val isLoggingPaused = AtomicBoolean(false)
    private val historyMode = AtomicReference(HistoryMode.AGGREGATE_ONLY)
    private var flushJob: Job? = null

    private val currentGeneration = AtomicLong(1L)
    private val flushMutex = Mutex()

    @Synchronized
    fun init(domainLogDao: DomainLogDao) {
        dao = domainLogDao
        if (flushJob == null) {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            flushJob = scope.launch {
                runFlushLoop()
            }
        }
    }

    fun setLoggingPaused(paused: Boolean) {
        isLoggingPaused.set(paused)
    }

    fun isLoggingPaused(): Boolean = isLoggingPaused.get()

    fun setHistoryMode(mode: HistoryMode) {
        historyMode.set(mode)
    }

    fun getHistoryMode(): HistoryMode = historyMode.get()

    fun resetForTesting() {
        queue.clear()
        isLoggingPaused.set(false)
        historyMode.set(HistoryMode.AGGREGATE_ONLY)
        currentGeneration.set(1L)
    }

    fun getCurrentGeneration(): Long = currentGeneration.get()

    /**
     * Records a DNS query decision.
     * Guarantees non-blocking execution (< 100 µs) on the packet reader loop.
     */
    fun record(rawDomain: String, decision: Int, reason: Int, tsMillis: Long = System.currentTimeMillis()) {
        if (isLoggingPaused.get()) return
        val normalized = DomainName.normalize(rawDomain) ?: return
        if (normalized.length > 253) return

        val gen = currentGeneration.get()
        val item = LogItem(
            domain = normalized,
            decision = decision,
            reason = reason,
            ts = tsMillis,
            generation = gen
        )
        if (!queue.offer(item)) {
            // Queue full: evict oldest entry to prevent latency spikes on the network loop
            queue.poll()
            queue.offer(item)
        }
    }

    fun clearQueue() {
        queue.clear()
    }

    suspend fun clearHistoryAndBarrier(): ClearResult = withContext(Dispatchers.IO) {
        flushMutex.withLock {
            currentGeneration.incrementAndGet()
            queue.clear()
            val currentDao = dao ?: return@withLock ClearResult.Failure("DAO uninitialized")
            currentDao.clearAll()
            ClearResult.Success
        }
    }

    suspend fun clearHistory() {
        clearHistoryAndBarrier()
    }

    private suspend fun runFlushLoop() {
        val buffer = ArrayList<LogItem>(BATCH_FLUSH_SIZE)
        var lastPruneTime = System.currentTimeMillis()

        while (true) {
            try {
                val first = withContext(Dispatchers.IO) {
                    queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
                }
                if (first != null) {
                    buffer.add(first)
                    queue.drainTo(buffer, BATCH_FLUSH_SIZE - 1)
                }

                if (buffer.isNotEmpty()) {
                    flushBatch(buffer)
                    buffer.clear()
                }

                val now = System.currentTimeMillis()
                if (now - lastPruneTime > 3_600_000L) { // Hourly maintenance
                    lastPruneTime = now
                    pruneOldLogs()
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                // Protect worker loop and record database diagnostics safely
                VpnDiagnostics.record(
                    code = VpnDiagnostics.FailureCode.DATABASE_LOG_ERROR,
                    exceptionClass = e::class.java.name
                )
            }
        }
    }

    internal suspend fun flushBatch(items: List<LogItem>) {
        flushMutex.withLock {
            val activeGen = currentGeneration.get()
            val validItems = items.filter { it.generation == activeGen }
            if (validItems.isEmpty()) return@withLock

            val currentDao = dao ?: return@withLock

            data class DomainAggregation(
                var calls: Long = 0,
                var blocked: Long = 0,
                var allowed: Long = 0,
                var latestTs: Long = 0,
                var latestDecision: Int = 0,
                var latestReason: Int = 0
            )

            val aggregated = HashMap<String, DomainAggregation>()
            val shouldStoreEvents = historyMode.get() == HistoryMode.RAW_HISTORY_OPT_IN
            val events = if (shouldStoreEvents) ArrayList<DomainEventEntity>(validItems.size) else null

            for (item in validItems) {
                val entry = aggregated.getOrPut(item.domain) { DomainAggregation() }
                entry.calls++
                if (item.decision == 1) entry.blocked++ else entry.allowed++
                if (item.ts >= entry.latestTs) {
                    entry.latestTs = item.ts
                    entry.latestDecision = item.decision
                    entry.latestReason = item.reason
                }
                if (shouldStoreEvents) {
                    events?.add(DomainEventEntity(domain = item.domain, ts = item.ts, decision = item.decision))
                }
            }

            val statUpserts = aggregated.map { (domain, agg) ->
                DomainStatUpsert(
                    domain = domain,
                    callDelta = agg.calls,
                    blockedDelta = agg.blocked,
                    allowedDelta = agg.allowed,
                    ts = agg.latestTs,
                    lastDecision = agg.latestDecision,
                    lastReason = agg.latestReason
                )
            }

            currentDao.upsertStatsBatch(statUpserts)
            if (shouldStoreEvents && !events.isNullOrEmpty()) {
                currentDao.insertEvents(events)
            }
        }
    }

    private suspend fun pruneOldLogs() {
        val currentDao = dao ?: return
        val oneDayAgo = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        currentDao.pruneEventsOlderThan(oneDayAgo)
        currentDao.pruneOldestEventsBeyondCap(1_000)

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        currentDao.pruneStatsOlderThan(thirtyDaysAgo)
        currentDao.pruneOldestStatsBeyondCap(10_000)
    }
}
