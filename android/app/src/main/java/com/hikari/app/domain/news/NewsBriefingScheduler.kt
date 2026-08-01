package com.hikari.app.domain.news

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant den täglichen News-Briefing-Worker zur gewählten Uhrzeit
 * ([timeMinutes] = Minuten seit Mitternacht). UPDATE ersetzt einen vorhandenen
 * Zeitplan — beim Umschalten der Uhrzeit bleibt genau ein Work bestehen.
 */
@Singleton
class NewsBriefingScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    fun schedule(timeMinutes: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<NewsBriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNextRun(timeMinutes), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun cancel() {
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
    }

    /** Millisekunden bis zum nächsten Zeitpunkt — heute, wenn noch kommend, sonst morgen. */
    internal fun delayUntilNextRun(timeMinutes: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, timeMinutes / 60)
            set(Calendar.MINUTE, timeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }

    companion object {
        private const val UNIQUE_NAME = "news-briefing-daily"
    }
}
