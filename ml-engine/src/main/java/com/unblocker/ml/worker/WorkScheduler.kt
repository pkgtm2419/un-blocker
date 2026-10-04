package com.unblocker.ml.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsulates the scheduling logic for the ML batch worker.
 *
 * Configures the **1:00 AM** periodic schedule with `RequiresCharging` and `RequiresDeviceIdle`
 * constraints to guarantee zero impact on user battery life.
 *
 * Also provides a throttled daytime fallback trigger ([scheduleBacklogScan]) if the
 * log backlog exceeds a threshold (e.g., if the user never charged the device overnight).
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Schedules the nightly ML batch worker.
     *
     * Calculates the initial delay so the first execution occurs at **1:00 AM**.
     * Subsequent executions happen every 24 hours.
     *
     * Constraints:
     * - `setRequiresCharging(true)`: Don't drain the battery.
     * - `setRequiresDeviceIdle(true)`: Don't run while the user is actively using the phone.
     */
    fun scheduleNightlyBatch() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var nextRun = now.with(LocalTime.of(1, 0)) // 1:00 AM

        // If it's already past 1:00 AM today, schedule for 1:00 AM tomorrow
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1)
        }

        val initialDelay = Duration.between(now, nextRun).toMillis()

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<BatchDomainAnalysisWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BatchDomainAnalysisWorker.WORK_NAME_NIGHTLY,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already enqueued
            request
        )
    }

    /**
     * Enqueues a one-off, throttled daytime scan.
     *
     * Called by the VPN engine when [com.unblocker.data.repository.LogRepository.countUnanalyzedLogs]
     * exceeds [BatchDomainAnalysisWorker.BACKLOG_THRESHOLD].
     *
     * Passes `KEY_IS_BACKLOG_RUN = true` to the worker, which limits the batch size
     * (e.g. 500 domains) to prevent thermal/UI jank during daytime use.
     *
     * Constraints:
     * - `setRequiresBatteryNotLow(true)`: Don't run if the battery is critical.
     */
    fun scheduleBacklogScan() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val inputData = Data.Builder()
            .putBoolean(BatchDomainAnalysisWorker.KEY_IS_BACKLOG_RUN, true)
            .build()

        val request = OneTimeWorkRequestBuilder<BatchDomainAnalysisWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        // Use APPEND_OR_REPLACE so if a backlog scan is already running/queued, we don't
        // pile up multiple scans.
        WorkManager.getInstance(context).enqueueUniqueWork(
            BatchDomainAnalysisWorker.WORK_NAME_BACKLOG,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
