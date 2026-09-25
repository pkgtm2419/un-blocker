package com.unblocker.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val healthCheckService = HealthCheckService(applicationContext)
            healthCheckService.performHealthCheck()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
