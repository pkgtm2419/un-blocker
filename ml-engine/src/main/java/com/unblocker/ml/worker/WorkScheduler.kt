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
 * Enqueues periodic and backlog analysis through WorkManager.
 *
 * Requests require a non-low battery and no network, with neither charging nor idle constraints.
 * Execution times remain subject to WorkManager and Android scheduling.
 *
 * @param context Application context used to obtain WorkManager.
 */
@Singleton
class WorkScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Keeps an existing periodic request or enqueues a new one repeating every 24 hours.
         *
         * The initial delay targets the next local 1 AM; WorkManager does not guarantee that exact time.
         * The request requires battery-not-low and has no charging, idle, or network requirement.
         */
        fun scheduleNightlyBatch() {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            var nextRun = now.with(LocalTime.of(1, 0)) // 1:00 AM

            // If it's already past 1:00 AM today, schedule for 1:00 AM tomorrow
            if (now.isAfter(nextRun)) {
                nextRun = nextRun.plusDays(1)
            }

            val initialDelay = Duration.between(now, nextRun).toMillis()

            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<BatchDomainAnalysisWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BatchDomainAnalysisWorker.WORK_NAME_NIGHTLY,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already enqueued
                request,
            )
        }

        /**
         * Appends a one-time, battery-not-low backlog request with the smaller batch input flag.
         *
         * APPEND_OR_REPLACE preserves WorkManager chaining semantics; repeated calls can queue multiple
         * scans. The caller decides whether the backlog warrants scheduling.
         */
        fun scheduleBacklogScan() {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

            val inputData =
                Data
                    .Builder()
                    .putBoolean(BatchDomainAnalysisWorker.KEY_IS_BACKLOG_RUN, true)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<BatchDomainAnalysisWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build()

            // Append to the existing chain, replacing failed or cancelled prerequisites as WorkManager specifies.
            WorkManager.getInstance(context).enqueueUniqueWork(
                BatchDomainAnalysisWorker.WORK_NAME_BACKLOG,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
