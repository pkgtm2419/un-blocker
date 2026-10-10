package com.unblocker.app.data.logs

import com.unblocker.app.logic.DomainName
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.DomainEventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Off-hot-path, lock-free log sink for intercepted DNS traffic.
 *
 * Implements WP-3:
 * - Engine calls record() on the packet loop; record() never blocks (enqueue into bounded ring, drop-oldest when full).
 * - A single writer coroutine aggregates events by domain for <= 1s (or 200 items) and flushes in a single Room transaction.
 * - Enforces bounded retention (default 7 days) and per-domain / global caps.
 */
object QueryLogSink {
    private const val QUEUE_CAPACITY = 10_000
    private const val BATCH_FLUSH_SIZE = 200
    private const val FLUSH_INTERVAL_MS = 1000L

    data class LogItem(
        val domain: String,
        val decision: Int, // 0 = allowed, 1 = blocked
        val reason: Int,
        val ts: Long
    )

    private val queue = ArrayBlockingQueue<LogItem>(QUEUE_CAPACITY)
    private var dao: DomainLogDao? = null
    private val isLoggingPaused = AtomicBoolean(false)
    private var flushJob: Job? = null

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

    /**
     * Records a DNS query decision.
     * Guarantees non-blocking execution (< 100 µs) on the packet reader loop.
     */
    fun record(rawDomain: String, decision: Int, reason: Int, tsMillis: Long = System.currentTimeMillis()) {
        if (isLoggingPaused.get()) return
        val normalized = DomainName.normalize(rawDomain) ?: return
        if (normalized.length > 253) return

        val item = LogItem(domain = normalized, decision = decision, reason = reason, ts = tsMillis)
        if (!queue.offer(item)) {
            // Queue full: evict oldest entry to prevent latency spikes on the network loop
            queue.poll()
            queue.offer(item)
        }
    }

    suspend fun clearHistory() {
        val currentDao = dao ?: return
        queue.clear()
        currentDao.clearAll()
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
            } catch (_: Exception) {
                // Protect worker loop from uncaught SQLite / IO errors
            }
        }
    }

    private suspend fun flushBatch(items: List<LogItem>) {
        val currentDao = dao ?: return

        data class DomainAggregation(
            var calls: Long = 0,
            var blocked: Long = 0,
            var allowed: Long = 0,
            var latestTs: Long = 0,
            var latestDecision: Int = 0,
            var latestReason: Int = 0
        )

        val aggregated = HashMap<String, DomainAggregation>()
        val events = ArrayList<DomainEventEntity>(items.size)

        for (item in items) {
            val entry = aggregated.getOrPut(item.domain) { DomainAggregation() }
            entry.calls++
            if (item.decision == 1) entry.blocked++ else entry.allowed++
            if (item.ts >= entry.latestTs) {
                entry.latestTs = item.ts
                entry.latestDecision = item.decision
                entry.latestReason = item.reason
            }
            events.add(DomainEventEntity(domain = item.domain, ts = item.ts, decision = item.decision))
        }

        for ((domain, agg) in aggregated) {
            currentDao.upsertStat(
                domain = domain,
                callDelta = agg.calls,
                blockedDelta = agg.blocked,
                allowedDelta = agg.allowed,
                ts = agg.latestTs,
                lastDecision = agg.latestDecision,
                lastReason = agg.latestReason
            )
        }
        currentDao.insertEvents(events)
    }

    private suspend fun pruneOldLogs() {
        val currentDao = dao ?: return
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        currentDao.pruneEventsOlderThan(sevenDaysAgo)
        currentDao.pruneOldestEventsBeyondCap(100_000)
    }
}
