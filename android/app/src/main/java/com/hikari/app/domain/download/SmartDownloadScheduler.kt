package com.hikari.app.domain.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the WorkManager call so the Application can schedule the periodic
 * Smart-Downloads job once on boot. The worker itself reads the user pref
 * and skips if disabled, so we always schedule.
 */
@Singleton
class SmartDownloadScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WLAN only
            .setRequiresBatteryNotLow(true)
            .build()

        val req = PeriodicWorkRequestBuilder<SmartDownloadWorker>(
            repeatInterval = REPEAT_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = FLEX_HOURS,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    fun runOnceNow() {
        val req = androidx.work.OneTimeWorkRequestBuilder<SmartDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            ONE_SHOT_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            req,
        )
    }

    companion object {
        private const val UNIQUE_NAME = "smart-download-periodic"
        private const val ONE_SHOT_NAME = "smart-download-one-shot"
        private const val REPEAT_HOURS = 6L
        private const val FLEX_HOURS = 1L
    }
}
