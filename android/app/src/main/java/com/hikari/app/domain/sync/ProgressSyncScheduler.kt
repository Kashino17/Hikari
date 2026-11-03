package com.hikari.app.domain.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sorgt dafür, dass offline entstandener Wiedergabefortschritt nachgezogen
 * wird, sobald das Gerät wieder Netz hat. Der periodische Lauf ist die
 * Rückfallebene; nach einem fehlgeschlagenen Upload wird zusätzlich sofort
 * ein einmaliger, netzgebundener Lauf eingeplant.
 */
@Singleton
class ProgressSyncScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<ProgressSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** KEEP: ein bereits wartender Lauf reicht, auch bei mehreren Save-Aufrufen. */
    fun enqueueRetry() {
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            RETRY_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val PERIODIC_WORK = "playback-progress-sync-periodic"
        const val RETRY_WORK = "playback-progress-sync-retry"
    }
}
