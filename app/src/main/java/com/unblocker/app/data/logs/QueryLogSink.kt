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
 * Queues intercepted DNS decisions for database writes outside the packet-processing path.
 *
 * Both aggregate rows and optional raw event rows contain readable domains. Aggregate-only mode is
 * the default. A generation barrier and mutex serialize history clearing against batch writes.
 * Hourly maintenance prunes events to 24 hours / 1,000 rows and aggregates to 30 days / 10,000 rows;
 * these are maintenance targets rather than strict per-insert bounds. Write failures are recorded
 * through diagnostics without including domain names.
 */
object QueryLogSink {
    private const val QUEUE_CAPACITY = 10_000
    private const val BATCH_FLUSH_SIZE = 200
    private const val FLUSH_INTERVAL_MS = 1000L
    private const val MAX_DOMAIN_CHARACTERS = 253
    private const val PRUNE_INTERVAL_MS = 3_600_000L
    private const val EVENT_RETENTION_MS = 24L * 60 * 60 * 1000
    private const val AGGREGATE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_RETAINED_EVENTS = 1_000
    private const val MAX_RETAINED_AGGREGATES = 10_000

    /**
     * Controls whether batch writes include raw chronological events in addition to aggregates.
     */
    enum class HistoryMode {
        AGGREGATE_ONLY,
        RAW_HISTORY_OPT_IN,
    }

    /**
     * Outcome of clearing persisted history after advancing the generation barrier.
     */
    sealed interface ClearResult {
        /**
         * The DAO clear operation completed.
         */
        data object Success : ClearResult

        /**
         * The history could not be cleared because storage was unavailable.
         *
         * @property reason Explanation of the unavailable storage.
         */
        data class Failure(
            val reason: String,
        ) : ClearResult
    }

    /**
     * One normalized decision waiting for batch aggregation.
     *
     * @property domain Readable normalized DNS host.
     * @property decision Decision code: zero for allowed, one for blocked.
     * @property reason Filtering reason code recorded with the latest decision.
     * @property ts Observation timestamp in Unix epoch milliseconds.
     * @property generation Clear-barrier generation captured when the item was queued.
     */
    data class LogItem(
        val domain: String,
        val decision: Int, // 0 = allowed, 1 = blocked
        val reason: Int,
        val ts: Long,
        val generation: Long = 1L,
    )

    private val queue = ArrayBlockingQueue<LogItem>(QUEUE_CAPACITY)
    private var dao: DomainLogDao? = null
    private val isLoggingPaused = AtomicBoolean(false)
    private val historyMode = AtomicReference(HistoryMode.AGGREGATE_ONLY)
    private var flushJob: Job? = null

    private val currentGeneration = AtomicLong(1L)
    private val flushMutex = Mutex()

    /**
     * Sets the DAO and starts the process-owned flush worker if it has not been started.
     *
     * @param domainLogDao Storage used for aggregates and optional raw event rows.
     */
    @Synchronized
    fun init(domainLogDao: DomainLogDao) {
        dao = domainLogDao
        if (flushJob == null) {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            flushJob =
                scope.launch {
                    runFlushLoop()
                }
        }
    }

    /**
     * Sets whether new calls to [record] are ignored.
     *
     * @param paused Whether new capture should be paused; already queued items are retained.
     */
    fun setLoggingPaused(paused: Boolean) {
        isLoggingPaused.set(paused)
    }

    /**
     * Returns the current capture pause flag.
     *
     * @return Whether new calls to [record] are ignored.
     */
    fun isLoggingPaused(): Boolean = isLoggingPaused.get()

    /**
     * Sets the persistence mode used when subsequent batches are flushed.
     *
     * @param mode Whether raw chronological event rows should accompany aggregates.
     */
    fun setHistoryMode(mode: HistoryMode) {
        historyMode.set(mode)
    }

    /**
     * Returns the current batch persistence mode.
     *
     * @return Aggregate-only or raw-history mode.
     */
    fun getHistoryMode(): HistoryMode = historyMode.get()

    /**
     * Clears queued items and resets flags and generation for isolated tests; does not clear the DAO.
     */
    fun resetForTesting() {
        queue.clear()
        isLoggingPaused.set(false)
        historyMode.set(HistoryMode.AGGREGATE_ONLY)
        currentGeneration.set(1L)
    }

    /**
     * Returns the current clear-barrier generation.
     *
     * @return Generation assigned to newly queued items.
     */
    fun getCurrentGeneration(): Long = currentGeneration.get()

    /**
     * Normalizes and queues a decision without performing database I/O.
     *
     * Paused or invalid input is ignored. When the bounded queue is full, an oldest item is evicted
     * before another enqueue attempt. Queue operations may briefly acquire their internal lock.
     *
     * @param rawDomain Host to normalize for readable local log storage.
     * @param decision Decision code: zero for allowed, one for blocked.
     * @param reason Filtering reason code associated with the decision.
     * @param tsMillis Observation timestamp in Unix epoch milliseconds.
     */
    fun record(
        rawDomain: String,
        decision: Int,
        reason: Int,
        tsMillis: Long = System.currentTimeMillis(),
    ) {
        if (isLoggingPaused.get()) return
        val normalized = DomainName.normalize(rawDomain) ?: return
        if (normalized.length > MAX_DOMAIN_CHARACTERS) return

        val generation = currentGeneration.get()
        val item =
            LogItem(
                domain = normalized,
                decision = decision,
                reason = reason,
                ts = tsMillis,
                generation = generation,
            )
        if (!queue.offer(item)) {
            // Queue full: evict oldest entry to prevent latency spikes on the network loop
            queue.poll()
            queue.offer(item)
        }
    }

    /**
     * Drops pending queued items without deleting persisted history or advancing the generation.
     */
    fun clearQueue() {
        queue.clear()
    }

    /**
     * Advances the generation, clears the queue, and deletes history while excluding batch writes.
     *
     * @return Success after the DAO clear, or failure when the DAO has not been initialized.
     * @throws Exception If the DAO clear operation fails; database exceptions propagate to the caller.
     */
    suspend fun clearHistoryAndBarrier(): ClearResult =
        withContext(Dispatchers.IO) {
            flushMutex.withLock {
                currentGeneration.incrementAndGet()
                queue.clear()
                val currentDao = dao ?: return@withLock ClearResult.Failure("DAO uninitialized")
                currentDao.clearAll()
                ClearResult.Success
            }
        }

    /**
     * Clears history through the generation barrier, discarding the uninitialized-DAO result.
     *
     * @throws Exception If the DAO clear operation fails.
     */
    suspend fun clearHistory() {
        clearHistoryAndBarrier()
    }

    private suspend fun runFlushLoop() {
        val buffer = ArrayList<LogItem>(BATCH_FLUSH_SIZE)
        var lastPruneTime = System.currentTimeMillis()

        // Clean up any stale records that expired while the process was stopped
        try {
            pruneOldLogs()
        } catch (e: Exception) {
            VpnDiagnostics.record(
                code = VpnDiagnostics.FailureCode.DATABASE_LOG_ERROR,
                exceptionClass = e::class.java.name,
            )
        }

        while (true) {
            try {
                val first =
                    withContext(Dispatchers.IO) {
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
                if (now - lastPruneTime > PRUNE_INTERVAL_MS) { // Hourly maintenance
                    lastPruneTime = now
                    pruneOldLogs()
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                // Protect worker loop and record database diagnostics safely
                VpnDiagnostics.record(
                    code = VpnDiagnostics.FailureCode.DATABASE_LOG_ERROR,
                    exceptionClass = e::class.java.name,
                )
            }
        }
    }

    internal suspend fun flushBatch(items: List<LogItem>) {
        flushMutex.withLock {
            val activeGeneration = currentGeneration.get()
            val validItems = items.filter { it.generation == activeGeneration }
            if (validItems.isEmpty()) return@withLock

            val currentDao = dao ?: return@withLock

            data class DomainAggregation(
                var calls: Long = 0,
                var blocked: Long = 0,
                var allowed: Long = 0,
                var latestTs: Long = 0,
                var latestDecision: Int = 0,
                var latestReason: Int = 0,
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

            val statUpserts =
                aggregated.map { (domain, aggregation) ->
                    DomainStatUpsert(
                        domain = domain,
                        callDelta = aggregation.calls,
                        blockedDelta = aggregation.blocked,
                        allowedDelta = aggregation.allowed,
                        ts = aggregation.latestTs,
                        lastDecision = aggregation.latestDecision,
                        lastReason = aggregation.latestReason,
                    )
                }

            currentDao.upsertStatsBatch(statUpserts)
            if (shouldStoreEvents && !events.isNullOrEmpty()) {
                currentDao.insertEvents(events)
            }
        }
    }

    /**
     * Prunes expired events and aggregates against configured retention and global cap limits.
     *
     * Deletes raw events older than [EVENT_RETENTION_MS] (24 hours) or beyond [MAX_RETAINED_EVENTS] (1,000),
     * and aggregated statistics older than [AGGREGATE_RETENTION_MS] (30 days) or beyond [MAX_RETAINED_AGGREGATES] (10,000).
     */
    internal suspend fun pruneOldLogs() {
        val currentDao = dao ?: return
        val oneDayAgo = System.currentTimeMillis() - EVENT_RETENTION_MS
        currentDao.pruneEventsOlderThan(oneDayAgo)
        currentDao.pruneOldestEventsBeyondCap(MAX_RETAINED_EVENTS)

        val thirtyDaysAgo = System.currentTimeMillis() - AGGREGATE_RETENTION_MS
        currentDao.pruneStatsOlderThan(thirtyDaysAgo)
        currentDao.pruneOldestStatsBeyondCap(MAX_RETAINED_AGGREGATES)
    }
}
