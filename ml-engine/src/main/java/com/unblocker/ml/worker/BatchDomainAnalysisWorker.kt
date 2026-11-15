package com.unblocker.ml.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unblocker.data.repository.FilterRepository
import com.unblocker.data.repository.LogRepository
import com.unblocker.ml.classifier.DomainClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processes legacy DNS logs with the injected on-device classifier.
 *
 * Each run reads a bounded log batch, deduplicates names within that batch, persists scores at
 * or above the confidence threshold, and marks all fetched row IDs analyzed. A non-backlog run
 * also prunes logs older than seven days, unless its initial batch is empty. Scheduling is
 * best-effort WorkManager work; the scheduler configures battery-not-low, not charging or idle.
 *
 * @param appContext Application context supplied by WorkManager.
 * @param workerParams WorkManager execution metadata and input data.
 * @param classifier On-device scorer for each distinct name in the batch.
 * @param logRepository Legacy DNS log source and analysis-state sink.
 * @param filterRepository Persists inferred rows and refreshes the library indices.
 */
@HiltWorker
class BatchDomainAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val classifier: DomainClassifier,
    private val logRepository: LogRepository,
    private val filterRepository: FilterRepository
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Stable worker limits, unique work names, and input keys.
     */
    companion object {
        /** Inclusive heuristic score threshold for creating a block rule. */
        const val CONFIDENCE_THRESHOLD = 0.88f

        /** Maximum legacy log rows fetched per periodic run, before domain deduplication. */
        const val BATCH_LIMIT = 10_000

        /** Legacy log backlog threshold available to callers deciding whether to enqueue work. */
        const val BACKLOG_THRESHOLD = 5_000

        /** Maximum legacy log rows fetched for a backlog run, before domain deduplication. */
        const val BACKLOG_BATCH_SIZE = 500

        /** WorkManager unique work name for the nightly job. */
        const val WORK_NAME_NIGHTLY = "unblocker_nightly_batch"

        /** WorkManager unique work name for the daytime backlog scan. */
        const val WORK_NAME_BACKLOG = "unblocker_backlog_scan"

        /** Input data key: true for throttled daytime backlog scans. */
        const val KEY_IS_BACKLOG_RUN = "is_backlog_run"
    }

    /**
     * Runs scoring on Default and database work on IO.
     *
     * A stopped worker exits the scoring loop but still attempts to mark all fetched rows analyzed.
     * Dependency failures are not converted to an explicit retry or failure result here.
     *
     * @return Success when the batch is empty or the processing path completes.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val isBacklogRun = inputData.getBoolean(KEY_IS_BACKLOG_RUN, false)
        val batchLimit   = if (isBacklogRun) BACKLOG_BATCH_SIZE else BATCH_LIMIT

        // ── Step 1: Fetch unanalyzed logs ──────────────────────────────────
        val unanalyzedLogs = withContext(Dispatchers.IO) {
            logRepository.getUnanalyzedLogs(limit = batchLimit)
        }
        if (unanalyzedLogs.isEmpty()) return@withContext Result.success()

        // Deduplicate names within the fetched batch; existing rule rows are not filtered out.
        val uniqueDomains = unanalyzedLogs.map { it.domain }.distinct()

        // Score each distinct domain using the injected classifier.
        val newRules = mutableListOf<Pair<String, Float>>()
        for (domain in uniqueDomains) {
            if (isStopped) break   // Honour WorkManager cancellation
            val score = classifier.predictAdProbability(domain)
            if (score >= CONFIDENCE_THRESHOLD) {
                newRules += domain to score
            }
        }

        // ── Step 6 & 7: Commit rules + hot-swap Bloom Filter ───────────────
        withContext(Dispatchers.IO) {
            if (newRules.isNotEmpty()) {
                filterRepository.insertMlBlockRules(newRules)
                // Bloom Filter reload is triggered inside insertMlBlockRules()
            }

            // ── Step 8: Mark logs as analyzed ─────────────────────────────
            val processedIds = unanalyzedLogs.map { it.id }
            logRepository.markLogsAsAnalyzed(processedIds)

            // ── Step 9: Prune old logs (7-day window) ─────────────────────
            if (!isBacklogRun) {
                logRepository.pruneOldLogs(daysToKeep = 7)
            }
        }

        Result.success()
    }
}
