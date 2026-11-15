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
 * Buffers legacy plaintext DNS log rows in a 512-entry, drop-oldest channel.
 *
 * An application-lifetime IO coroutine collects at most 100 rows over a two-second window after
 * its first row arrives. Caught write failures discard that batch; enqueue success is not a
 * persistence guarantee. The active app's QueryLogSink is a separate logging path.
 *
 * @param dnsLogDao Database sink and query source for legacy DNS log rows.
 */
@Singleton
class LogRepository @Inject constructor(
    private val dnsLogDao: DnsLogDao
) {

    private companion object {
        const val QUEUE_CAPACITY = 512
        const val MAX_BATCH_SIZE = 100
        const val BATCH_WINDOW_MS = 2_000L
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Non-blocking channel for fire-and-forget log enqueueing
    private val logChannel = Channel<DnsLogEntity>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        startBatchCollector()
    }

    private fun startBatchCollector() {
        repositoryScope.launch {
            val batch = ArrayList<DnsLogEntity>(MAX_BATCH_SIZE)
            while (isActive) {
                try {
                    // Suspend waiting for the first log entry
                    val first = logChannel.receive()
                    batch.add(first)
                } catch (_: Exception) {
                    break
                }

                // Batch window: accumulate up to 100 entries within 2000ms
                val deadline = System.currentTimeMillis() + BATCH_WINDOW_MS
                while (batch.size < MAX_BATCH_SIZE) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) break
                    val item = withTimeoutOrNull(remaining) {
                        try {
                            logChannel.receive()
                        } catch (_: Exception) {
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
     * Attempts to enqueue a legacy DNS observation without suspending.
     *
     * @param domain Readable queried domain stored as supplied.
     * @param isBlocked Decision associated with this observation.
     * @return Whether the channel accepted the row; older queued rows can be dropped on overflow.
     */
    fun enqueueDnsLog(domain: String, isBlocked: Boolean): Boolean {
        val entity = DnsLogEntity(domain = domain, isBlocked = isBlocked)
        return logChannel.trySend(entity).isSuccess
    }

    /**
     * Enqueues a legacy observation through the buffered path.
     *
     * @param domain Readable queried domain.
     * @param isBlocked Decision associated with this observation.
     */
    suspend fun recordDnsRequest(domain: String, isBlocked: Boolean) {
        enqueueDnsLog(domain, isBlocked)
    }

    /**
     * Writes observations directly through the DAO rather than the buffered channel.
     *
     * @param entries Readable domain and blocked-decision pairs.
     */
    suspend fun recordDnsRequests(entries: List<Pair<String, Boolean>>) {
        val entities = entries.map { (domain, blocked) ->
            DnsLogEntity(domain = domain, isBlocked = blocked)
        }
        dnsLogDao.insertLogs(entities)
    }

    /**
     * The 100 most recent legacy log rows observed in descending timestamp order.
     */
    val recentLogsFlow: Flow<List<DnsLogEntity>> = dnsLogDao.getRecentLogsFlow()

    /**
     * Reads unprocessed legacy observations.
     *
     * @param limit Maximum rows requested from SQLite.
     * @return Unanalyzed rows ordered oldest first.
     */
    suspend fun getUnanalyzedLogs(limit: Int = 10_000): List<DnsLogEntity> =
        dnsLogDao.getUnanalyzedLogs(limit)

    /**
     * Marks stored log rows as processed.
     *
     * @param ids Primary keys to update.
     */
    suspend fun markLogsAsAnalyzed(ids: List<Long>) = dnsLogDao.markLogsAsAnalyzed(ids)

    /**
     * Counts unprocessed legacy observations.
     *
     * @return Number of rows whose analysis flag is false.
     */
    suspend fun countUnanalyzedLogs(): Int = dnsLogDao.countUnanalyzedLogs()

    /**
     * Deletes observations older than a wall-clock cutoff.
     *
     * @param daysToKeep Retained age in fixed 24-hour days, measured back from the current time.
     */
    suspend fun pruneOldLogs(daysToKeep: Int = 7) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000)
        dnsLogDao.pruneOldLogs(cutoff)
    }

    /**
     * Deletes persisted legacy log rows; queued rows are not drained or invalidated.
     */
    suspend fun clearAll() = dnsLogDao.clearAll()
}
