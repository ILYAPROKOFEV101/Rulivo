package com.ilya.rulivo.core.dunta

import android.content.Context
import com.ilya.rulivo.RulivoApplication
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class SupportModeRecoveryWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = restartMutex.withLock {
        SupportModeDiagnostics.event("recovery_worker_started")
        if (!BatteryOptimizationController.isIgnoring(applicationContext)) {
            SupportModeDiagnostics.event(
                "recovery_worker_skipped",
                "reason=battery_optimization_enabled"
            )
            return@withLock Result.success()
        }

        val controller = (applicationContext as RulivoApplication).duntaSdkController
        if (!controller.isConfigured || !controller.isPeer) {
            SupportModeDiagnostics.event("recovery_worker_skipped", "reason=not_ready")
            return@withLock Result.success()
        }

        if (controller.restartIfEnabled()) {
            SupportModeDiagnostics.event("recovery_worker_finished", "result=success")
            Result.success()
        } else {
            SupportModeDiagnostics.event("recovery_worker_finished", "result=retry")
            Result.retry()
        }
    }

    private companion object {
        val restartMutex = Mutex()
    }
}

object SupportModeRecoveryScheduler {
    private const val WIFI_WORK_NAME = "support_mode_wifi_recovery"
    private const val CHARGING_WORK_NAME = "support_mode_charging_recovery"

    fun sync(context: Context, enabled: Boolean, batteryOptimizationExempt: Boolean) {
        if (!enabled || !batteryOptimizationExempt) {
            cancel(context)
            SupportModeDiagnostics.event("recovery_jobs_cancelled")
            return
        }

        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniquePeriodicWork(
            WIFI_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SupportModeRecoveryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            CHARGING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SupportModeRecoveryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresCharging(true)
                        .build()
                )
                .build()
        )
        SupportModeDiagnostics.event("recovery_jobs_scheduled")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).run {
            cancelUniqueWork(WIFI_WORK_NAME)
            cancelUniqueWork(CHARGING_WORK_NAME)
        }
    }
}
