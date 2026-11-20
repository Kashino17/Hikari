package com.hikari.app.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikari.app.MainActivity
import com.hikari.app.R
import org.json.JSONObject

/** Benachrichtigungen der Aufgaben: Ergebnis einer Aufnahme und das Briefing am Morgen. */
object TaskNotifications {
    private const val CH_RESULT = "tasks_result"
    private const val CH_BRIEF = "tasks_briefing"
    private const val CH_WORK = "tasks_working"
    const val WORKING_ID = 7700
    private const val FAILED_ID = 7701
    const val BRIEFING_ID = 7702
    private const val AMBER = 0xFFFBBF24.toInt()

    private val AREA = mapOf(
        "amt" to "Behörden", "geld" to "Finanzen", "job" to "Arbeit",
        "leute" to "Menschen", "haus" to "Alltag", "ich" to "Hobby",
    )
    private val KIND = mapOf("rechner" to "Am Rechner", "telefon" to "Telefonieren", "unterwegs" to "Unterwegs", "zuhause" to "Zuhause")

    private fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Aufgaben angelegt", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Meldet, wenn Hikari aus deiner Aufnahme Aufgaben gemacht hat."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BRIEF, "Aufgaben am Morgen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Um 7:30 die wichtigsten Dinge für den Tag."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WORK, "Aufgaben im Hintergrund", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Nur kurz sichtbar, während eine Aufnahme hochgeladen wird."
            },
        )
    }

    private fun allowed(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(ctx: Context, route: String, req: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, req,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun working(ctx: Context): Notification {
        channels(ctx)
        return NotificationCompat.Builder(ctx, CH_WORK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hikari sortiert deine Aufgaben")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    /** »3 Aufgaben angelegt« mit je einer Zeile, wo sie gelandet sind. */
    fun result(ctx: Context, captureId: String, items: List<JSONObject>, fallback: Boolean) {
        if (!allowed(ctx)) return
        channels(ctx)
        val id = 7800 + (captureId.hashCode() and 0x3ff)
        val lines = items.map { t ->
            val where = if (!t.isNull("project")) "${t.optString("project")}, Schritt ${t.optInt("step")}"
            else KIND[t.optString("kind")] ?: AREA[t.optString("area")].orEmpty()
            "${t.optString("title")} · $where"
        }
        val title = if (items.size == 1) "Aufgabe angelegt" else "${items.size} Aufgaben angelegt"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(6).forEach { style.addLine(it) }
        if (fallback) style.setSummaryText("ohne KI sortiert, bitte kurz prüfen")
        val undo = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, TaskActionReceiver::class.java).setAction(TaskActionReceiver.UNDO)
                .putExtra("id", captureId).putExtra("notif", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(ctx).notify(
            id,
            NotificationCompat.Builder(ctx, CH_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(AMBER)
                .setContentTitle(title)
                .setContentText(lines.joinToString(" · ") { it.substringBefore(" · ") })
                .setStyle(style)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAllowSystemGeneratedContextualActions(false)
                .setContentIntent(openApp(ctx, "tasks?page=new:$captureId", id))
                .addAction(0, "Ansehen", openApp(ctx, "tasks?page=new:$captureId", id + 1))
                .addAction(0, "Rückgängig", undo)
                .build(),
        )
    }

    fun failed(ctx: Context, text: String) {
        if (!allowed(ctx)) return
        channels(ctx)
        NotificationManagerCompat.from(ctx).notify(
            FAILED_ID,
            NotificationCompat.Builder(ctx, CH_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(AMBER)
                .setContentTitle("Aufgabe nicht angelegt")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, "profile?tab=aufgaben", FAILED_ID))
                .build(),
        )
    }

    fun briefing(ctx: Context, items: List<JSONObject>, minutes: Int) {
        if (!allowed(ctx) || items.isEmpty()) return
        channels(ctx)
        val title = "Dein Tag: ${items.size} ${if (items.size == 1) "Ding" else "Dinge"}, ca. $minutes min"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        items.forEachIndexed { i, t -> style.addLine("${i + 1}. ${t.optString("title")}") }
        val first = items.first().optString("id")
        NotificationManagerCompat.from(ctx).notify(
            BRIEFING_ID,
            NotificationCompat.Builder(ctx, CH_BRIEF)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(AMBER)
                .setContentTitle(title)
                .setContentText(items.joinToString(" · ") { it.optString("title") })
                .setStyle(style)
                .setAutoCancel(true)
                .setAllowSystemGeneratedContextualActions(false)
                .setContentIntent(openApp(ctx, "profile?tab=aufgaben", BRIEFING_ID))
                .addAction(0, "Los geht's", openApp(ctx, "tasks?page=focus:$first", BRIEFING_ID + 1))
                .build(),
        )
    }
}

/** Rückgängig aus der Benachrichtigung, Abhaken im Widget. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        when (intent.action) {
            UNDO -> {
                intent.getIntExtra("notif", 0).takeIf { it != 0 }?.let { NotificationManagerCompat.from(ctx).cancel(it) }
                TaskActionWorker.enqueue(ctx, TaskActionWorker.ACTION_UNDO, id)
            }
            DONE -> {
                TasksWidget.markDone(ctx, id)
                TaskActionWorker.enqueue(ctx, TaskActionWorker.ACTION_DONE, id)
            }
        }
    }

    companion object {
        const val UNDO = "com.hikari.app.tasks.UNDO"
        const val DONE = "com.hikari.app.tasks.DONE"
    }
}
