package com.hikari.app.domain.russian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hikari.app.MainActivity
import com.hikari.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RussianPodcastReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val store: RussianProgressStore,
    private val repo: RussianCourseRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val progress = store.state.value
        if (!shouldSendPodcastReminder(progress, store.today(), repo.podcastIndex.days.size)) return Result.success()
        val day = progress.unlockedPodcastDay(repo.podcastIndex.days.size)
        val lesson = repo.podcastIndex.day(day) ?: return Result.success()
        showNotification(lesson, RuSrs.visibleStreak(progress, store.today()))
        return Result.success()
    }

    private fun showNotification(lesson: RuDay, streak: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "russian/podcast")
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Zeit für deine Russisch-Lektion")
            .setContentText("Lektion ${lesson.day}: ${lesson.title} · $streak Tage Serie")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Lektion ${lesson.day}: ${lesson.title}\n$streak Tage Serie – bleib dran!"))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Russisch-Podcastkurs",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tägliche Erinnerung an deine nächste Podcast-Lektion."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "russian_podcast_reminder"
        const val NOTIFICATION_ID = 4302
    }
}
