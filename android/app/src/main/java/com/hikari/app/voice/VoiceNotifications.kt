package com.hikari.app.voice

import android.app.Notification
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
import com.hikari.app.MainActivity
import com.hikari.app.R
import com.hikari.app.data.api.dto.VoiceJobDto
import com.hikari.app.domain.voice.VoiceFormat

/**
 * Benachrichtigungen für Sprachnachrichten. Das Ergebnis kommt über einen
 * Kanal mit hoher Wichtigkeit und erscheint damit als Banner über WhatsApp.
 */
object VoiceNotifications {
    private const val CH_PROGRESS = "voice_progress"
    private const val CH_RESULT = "voice_result"
    private const val ID_BASE = 7300

    /** Gleiche ID für Fortschritt und Ergebnis: das Ergebnis ersetzt den Balken. */
    fun idFor(key: String): Int = ID_BASE + (key.hashCode() and 0x3fff)

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Sprachnachrichten: Fortschritt", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Sprachnachrichten: Zusammenfassung", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Erscheint, sobald Hikari Sprachnachrichten zusammengefasst hat" },
        )
    }

    private fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openIntent(ctx: Context, jobId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", if (jobId != null) "voice-notes/$jobId" else "voice-notes")
        }
        return PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun progress(ctx: Context, key: String, text: String, fraction: Float?): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hikari hört zu")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .setContentIntent(openIntent(ctx, null, idFor(key)))
            .build()
    }

    fun showProgress(ctx: Context, key: String, text: String, fraction: Float?) {
        if (!allowed(ctx)) return
        NotificationManagerCompat.from(ctx).notify(idFor(key), progress(ctx, key, text, fraction))
    }

    fun showResult(ctx: Context, key: String, job: VoiceJobDto) {
        if (!allowed(ctx)) return
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val id = idFor(key)
        val summary = job.summary
        val builder = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(ctx, job.id, id))
            .setSubText(VoiceFormat.meta(job.total, job.duration))
        if (job.status == "done" && summary != null) {
            val share = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, VoiceFormat.asText(summary, job.label))
                },
                "Zusammenfassung teilen",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder
                .setContentTitle(summary.title)
                .setContentText(summary.summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(VoiceFormat.notificationText(summary)))
                .addAction(
                    0,
                    "Teilen",
                    PendingIntent.getActivity(ctx, id + 1, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
        } else {
            val text = VoiceFormat.errorText(job.error)
            builder
                .setContentTitle("Sprachnachrichten nicht zusammengefasst")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nIn Hikari kannst du es erneut versuchen."))
        }
        // Erst den laufenden Fortschritt weg, sonst bleibt er als "ongoing" hängen.
        nm.cancel(id)
        nm.notify(id, builder.build())
    }

    fun showUploadFailed(ctx: Context, key: String, reason: String) {
        if (!allowed(ctx)) return
        ensureChannels(ctx)
        val id = idFor(key)
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sprachnachrichten nicht angekommen")
            .setContentText(reason)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null, id))
            .build()
        NotificationManagerCompat.from(ctx).cancel(id)
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun cancel(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(idFor(key))
}
