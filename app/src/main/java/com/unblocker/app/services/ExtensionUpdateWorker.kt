package com.unblocker.app.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unblocker.app.data.repository.ExtensionRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that updates extensions when the current auto-update setting is enabled.
 *
 * @param appContext Worker application context.
 * @param workerParams WorkManager execution parameters.
 */
class ExtensionUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    /**
     * Returns success when disabled or updates succeed, otherwise requests a retry.
     */
    override suspend fun doWork(): Result {
        val repo = ExtensionRepository.getInstance(applicationContext)
        if (!repo.autoUpdateEnabled.value) {
            return Result.success()
        }

        val result = repo.updateAll()
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    /**
     * Schedules and cancels periodic extension updates.
     */
    companion object {
        /**
         * Stable unique WorkManager name for periodic extension updates.
         */
        const val WORK_NAME = "ExtensionUpdatePeriodicWorker"

        /**
         * Schedules daily update work requiring unmetered connectivity and battery-not-low state.
         *
         * @param context Context used to obtain WorkManager; actual execution timing is system-managed.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * Cancels the unique periodic extension-update work.
         *
         * @param context Context used to obtain WorkManager.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
