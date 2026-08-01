package com.hikari.app.domain.news

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
import com.hikari.app.domain.repo.NewsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Holt einmal täglich das News-Briefing (force=false — das Backend cached
 * tagesweise) und zeigt eine Benachrichtigung. Der PendingIntent öffnet die
 * App direkt auf dem News-Tab.
 *
 * Bei Backend-Fehlern: bis zu 3 Versuche mit WorkManager-Backoff, danach
 * failure ohne Notification — die nächste reguläre Ausführung kommt morgen.
 */
@HiltWorker
class NewsBriefingWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val newsRepo: NewsRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val items = try {
            newsRepo.getBriefing(force = false)
        } catch (_: Exception) {
            return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
        if (items.isNotEmpty()) showNotification(items.size)
        return Result.success()
    }

    private fun showNotification(count: Int) {
        // Ab API 33 ist POST_NOTIFICATIONS eine Runtime-Permission — ohne sie
        // darf nicht gepostet werden. Der Worker läuft dann einfach leise durch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_NEWS)
        }
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Dein Tagesbericht ist da")
            .setContentText("$count neue Nachrichten für dich")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tagesbericht",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "news_digest"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_TO_NEWS = "news"
        private const val NOTIFICATION_ID = 4201
        private const val MAX_ATTEMPTS = 3
    }
}
