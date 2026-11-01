package com.unblocker.data.repository

import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.entity.DnsLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for DNS log entries with non-blocking buffered batch ingestion.
 *
 * Implements a non-blocking `Channel<DnsLogEntity>` with capacity 512 and `DROP_OLDEST`
 * overflow behavior, ensuring the high-speed VPN packet loop never blocks or suspends
 * on SQLite disk I/O.
 *
 * A single background collector on `Dispatchers.IO` batches up to 100 entries or flushes
 * every 2000ms.
 */
@Singleton
class LogRepository @Inject constructor(
    private val dnsLogDao: DnsLogDao
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Non-blocking channel for fire-and-forget log enqueueing
    private val logChannel = Channel<DnsLogEntity>(
        capacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        startBatchCollector()
    }

    private fun startBatchCollector() {
        repositoryScope.launch {
            val batch = ArrayList<DnsLogEntity>(100)
            while (isActive) {
                try {
                    // Suspend waiting for the first log entry
                    val first = logChannel.receive()
                    batch.add(first)
                } catch (e: Exception) {
                    break
                }

                // Batch window: accumulate up to 100 entries within 2000ms
                val deadline = System.currentTimeMillis() + 2000L
                while (batch.size < 100) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) break
                    val item = withTimeoutOrNull(remaining) {
                        try {
                            logChannel.receive()
                        } catch (e: Exception) {
                            null
                        }
                    } ?: break
                    batch.add(item)
                }

                if (batch.isNotEmpty()) {
                    runCatching {
                        dnsLogDao.insertLogs(batch)
                    }
                    batch.clear()
                }
            }
        }
    }

    /**
     * Non-blocking enqueue of a DNS log entry from the VPN packet thread.
     * Uses `trySend` to guarantee zero lock contention and zero suspension.
     */
    fun enqueueDnsLog(domain: String, isBlocked: Boolean): Boolean {
        val entity = DnsLogEntity(domain = domain, isBlocked = isBlocked)
        return logChannel.trySend(entity).isSuccess
    }

    /**
     * Records a single DNS request asynchronously via the non-blocking channel.
     * Preserves backward compatibility with existing callers.
     */
    suspend fun recordDnsRequest(domain: String, isBlocked: Boolean) {
        enqueueDnsLog(domain, isBlocked)
    }

    /**
     * Batch-records multiple DNS requests directly.
     */
    suspend fun recordDnsRequests(entries: List<Pair<String, Boolean>>) {
        val entities = entries.map { (domain, blocked) ->
            DnsLogEntity(domain = domain, isBlocked = blocked)
        }
        dnsLogDao.insertLogs(entities)
    }

    /** Real-time [Flow] of the 100 most-recent DNS log entries for the LogViewer UI. */
    val recentLogsFlow: Flow<List<DnsLogEntity>> = dnsLogDao.getRecentLogsFlow()

    /**
     * Returns up to [limit] unanalyzed log entries for the nightly ML batch worker.
     * Ordered by timestamp (oldest first) so the worker processes in chronological order.
     */
    suspend fun getUnanalyzedLogs(limit: Int = 10_000): List<DnsLogEntity> =
        dnsLogDao.getUnanalyzedLogs(limit)

    /** Marks the given log IDs as analyzed — prevents re-processing in future runs. */
    suspend fun markLogsAsAnalyzed(ids: List<Long>) = dnsLogDao.markLogsAsAnalyzed(ids)

    /** Returns the count of unanalyzed logs — used by the backlog fail-safe watchdog. */
    suspend fun countUnanalyzedLogs(): Int = dnsLogDao.countUnanalyzedLogs()

    /**
     * Deletes log entries older than [daysToKeep] days.
     * Called at the end of each nightly ML run to prevent database bloat.
     */
    suspend fun pruneOldLogs(daysToKeep: Int = 7) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000)
        dnsLogDao.pruneOldLogs(cutoff)
    }

    /** Wipes all DNS log data (user-initiated privacy reset). */
    suspend fun clearAll() = dnsLogDao.clearAll()
}
