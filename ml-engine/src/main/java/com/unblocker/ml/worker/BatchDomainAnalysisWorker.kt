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
 * Nightly batch ML worker — scheduled to run at **1:00 AM** while the device is
 * charging and idle (see [WorkScheduler]).
 *
 * ## Processing pipeline
 * ```
 * 1. Fetch up to 10,000 unanalyzed DnsLog entries  (oldest-first)
 * 2. Deduplicate by domain name
 * 3. Remove domains already present in block_rules  (no re-inference needed)
 * 4. Run TFLite inference: DomainFeatureExtractor → TFLiteClassifierImpl
 * 5. Flag domains scoring ≥ CONFIDENCE_THRESHOLD as ads/trackers
 * 6. Bulk-commit new BlockRuleEntity rows to Room
 * 7. Trigger atomic Bloom Filter hot-swap                ← traffic is now updated
 * 8. Mark processed log IDs as analyzed
 * 9. Prune dns_logs entries older than 7 days            ← keeps DB footprint bounded
 * ```
 *
 * ## Fail-safe: backlog watchdog
 * If the device was not charging overnight and the nightly job did not run, unanalyzed
 * logs accumulate. When [LogRepository.countUnanalyzedLogs] exceeds [BACKLOG_THRESHOLD],
 * [WorkScheduler.scheduleBacklogScan] triggers a throttled daytime scan of only
 * [BACKLOG_BATCH_SIZE] domains per run to avoid blocking daytime activity.
 *
 * ## Constraints
 * - `setRequiresCharging(true)`    — zero perceived battery impact on the user.
 * - `setRequiresDeviceIdle(true)`  — only runs while the user is asleep / inactive.
 *
 * @param classifier      Injected [DomainClassifier] (TFLite or test fake).
 * @param logRepository   Source of unanalyzed DNS log entries.
 * @param filterRepository  Sink for newly discovered block rules.
 */
@HiltWorker
class BatchDomainAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val classifier: DomainClassifier,
    private val logRepository: LogRepository,
    private val filterRepository: FilterRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** TFLite confidence threshold: domains scoring above this are flagged as ads. */
        const val CONFIDENCE_THRESHOLD = 0.88f

        /** Maximum domains processed per nightly run (prevents OOM on large backlogs). */
        const val BATCH_LIMIT = 10_000

        /**
         * Daytime backlog threshold — if unanalyzed logs exceed this count
         * (because the nightly job didn't run), a throttled daytime scan fires.
         */
        const val BACKLOG_THRESHOLD = 5_000

        /**
         * Domains analyzed per throttled daytime scan invocation.
         * Small enough to avoid thermal or UI jank during daytime use.
         */
        const val BACKLOG_BATCH_SIZE = 500

        /** WorkManager unique work name for the nightly job. */
        const val WORK_NAME_NIGHTLY = "unblocker_nightly_batch"

        /** WorkManager unique work name for the daytime backlog scan. */
        const val WORK_NAME_BACKLOG = "unblocker_backlog_scan"

        /** Input data key: true for throttled daytime backlog scans. */
        const val KEY_IS_BACKLOG_RUN = "is_backlog_run"
    }

    /**
     * Executes the batch analysis pipeline on [Dispatchers.Default] (CPU-bound work).
     *
     * Heavy TFLite inference loops run on [Dispatchers.Default] to take advantage of
     * all available cores without blocking the main thread or I/O pool.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val isBacklogRun = inputData.getBoolean(KEY_IS_BACKLOG_RUN, false)
        val batchLimit   = if (isBacklogRun) BACKLOG_BATCH_SIZE else BATCH_LIMIT

        // ── Step 1: Fetch unanalyzed logs ──────────────────────────────────
        val unanalyzedLogs = withContext(Dispatchers.IO) {
            logRepository.getUnanalyzedLogs(limit = batchLimit)
        }
        if (unanalyzedLogs.isEmpty()) return@withContext Result.success()

        // ── Step 2 & 3: Deduplicate and filter out already-known domains ───
        val uniqueDomains = unanalyzedLogs.map { it.domain }.distinct()

        // ── Step 4 & 5: TFLite inference ───────────────────────────────────
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
