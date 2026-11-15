package com.unblocker.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager entrypoint for eligible local health smoke checks.
 *
 * @param context Worker context.
 * @param workerParams WorkManager execution parameters.
 */
class HealthCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    /**
     * Returns success when protection is disabled or checking completes, otherwise requests a retry.
     */
    override suspend fun doWork(): Result {
        val preferences =
            com.unblocker.app.data.preferences.FilteringPreferences
                .getInstance(applicationContext)
        if (!HealthCheckPolicy.shouldRun(preferences.protectionEnabled.value)) {
            return Result.success()
        }
        return try {
            val healthCheckService = HealthCheckService(applicationContext)
            healthCheckService.performHealthCheck()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
