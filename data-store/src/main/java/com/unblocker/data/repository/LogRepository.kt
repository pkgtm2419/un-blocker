package com.unblocker.data.repository

import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.entity.DnsLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for DNS log entries.
 *
 * Abstracts Room DAO calls behind a clean API consumed by:
 *  - The VPN service (async [recordDnsRequest] fire-and-forget writes)
 *  - The LogViewer screen (reactive [recentLogsFlow])
 *  - The ML batch worker ([getUnanalyzedLogs], [markLogsAsAnalyzed], [pruneOldLogs])
 *  - The backlog watchdog ([countUnanalyzedLogs])
 */
@Singleton
class LogRepository @Inject constructor(
    private val dnsLogDao: DnsLogDao
) {

    /**
     * Asynchronously records a single DNS request intercepted by the VPN tunnel.
     *
     * This is called from the VPN packet loop inside a fire-and-forget coroutine —
     * it MUST complete quickly and MUST NOT throw.
     *
     * @param domain    The queried domain name.
     * @param isBlocked Whether the Bloom Filter blocked this request.
     */
    suspend fun recordDnsRequest(domain: String, isBlocked: Boolean) {
        dnsLogDao.insertLog(
            DnsLogEntity(domain = domain, isBlocked = isBlocked)
        )
    }

    /**
     * Batch-records multiple DNS requests (used when the VPN engine buffers writes
     * before flushing to reduce SQLite transaction overhead).
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
