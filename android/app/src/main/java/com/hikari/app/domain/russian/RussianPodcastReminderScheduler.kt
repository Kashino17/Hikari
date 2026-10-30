package com.hikari.app.domain.russian

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal fun shouldSendPodcastReminder(progress: RuProgress, today: Long, totalDays: Int): Boolean =
    progress.podcastLastCompletedDay != today && progress.podcastDayStars.size < totalDays

@Singleton
class RussianPodcastReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(timeMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<RussianPodcastReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNextRun(timeMinutes), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

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

    private companion object {
        const val UNIQUE_NAME = "russian-podcast-reminder-daily"
    }
}
