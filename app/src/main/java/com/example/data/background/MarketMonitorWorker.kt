package com.example.data.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.notification.PriceAlertNotificationManager
import com.example.data.repository.MarketRepository
import java.util.concurrent.TimeUnit

class MarketMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mode = BackgroundPreferences.getMode(applicationContext)
        if (mode == BackgroundMonitorMode.OFF) {
            return Result.success()
        }

        // Respect OS Battery Saver unless user explicitly overrides
        if (BackgroundPreferences.isBatterySaverActive(applicationContext) &&
            !BackgroundPreferences.getAllowOnBatterySaver(applicationContext)
        ) {
            val now = System.currentTimeMillis()
            BackgroundPreferences.recordRunDiagnostics(
                context = applicationContext,
                lastRunMs = now,
                nextRunMs = now + 15L * 60_000L,
                symbolsCovered = 0,
                durationMs = 0L,
                errors = listOf("Paused while Battery Saver active (override off)")
            )
            return Result.success()
        }

        return try {
            val repo = MarketRepository(applicationContext)
            repo.syncCalibrationHistoryFromRoom()
            val coordinator = AnalysisCoordinator(
                context = applicationContext,
                repository = repo,
                notificationManager = PriceAlertNotificationManager(applicationContext)
            )
            coordinator.runBackgroundWatchlistCycle(maxSymbolsPerRun = 10)
            Result.success()
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            BackgroundPreferences.recordRunDiagnostics(
                context = applicationContext,
                lastRunMs = now,
                nextRunMs = now + 15L * 60_000L,
                symbolsCovered = 0,
                durationMs = 0L,
                errors = listOf("Worker error: ${e.message ?: "unknown"}")
            )
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "tauric_standard_market_monitor"

        fun scheduleStandard(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MarketMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()

            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }
    }
}
