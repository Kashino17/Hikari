package com.hikari.app.ui.fitness

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hikari.app.MainActivity
import com.hikari.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Trainings-Erinnerungen: an Trainingstagen zur gewählten Uhrzeit (»Heute ist Trainingstag«),
 * abends um 20:30, wenn noch nicht trainiert wurde (»Serie retten«), montags 9 Uhr Wochenrückblick.
 * Einstellungen setzt die Fitness-Oberfläche über die Brücke (FitWebBridge.setReminders).
 *
 * Jede Erinnerung ist eine einmalige Arbeit auf die nächste echte Uhrzeit, die sich nach dem Lauf
 * selbst neu stellt. (Periodische 24-h-Arbeit wanderte jeden Tag nach hinten, sprang bei der
 * Zeitumstellung um eine Stunde und übernahm eine geänderte Uhrzeit nach dem ersten Lauf nie.)
 */
object FitReminders {
    private const val PREFS = "hikari_fit_reminders"
    private fun work(kind: String) = "fit-remind-$kind"
    private val OLD_PERIODIC = listOf("fit-reminder-train", "fit-reminder-streak", "fit-reminder-week")
    const val STREAK_MIN = 20 * 60 + 30
    const val CHANNEL_ID = "fitness_reminder"

    data class Config(val enabled: Boolean, val minutes: Int, val streak: Boolean, val days: Set<Int>, val configured: Boolean = false)

    fun read(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            enabled = p.getBoolean("enabled", false),
            minutes = p.getInt("minutes", 18 * 60),
            streak = p.getBoolean("streak", true),
            days = p.getString("days", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet(),
            configured = p.contains("enabled"),
        )
    }

    fun toJson(c: Config): String = JSONObject()
        .put("enabled", c.enabled).put("configured", c.configured).put("minutes", c.minutes).put("streak", c.streak)
        .put("days", org.json.JSONArray(c.days.sorted())).toString()

    /** Aus der Oberfläche: speichern und Arbeit neu planen. */
    fun apply(context: Context, json: String) {
        val o = JSONObject(json)
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", o.optBoolean("enabled", false))
            .putInt("minutes", o.optInt("minutes", 18 * 60).coerceIn(0, 24 * 60 - 1))
            .putBoolean("streak", o.optBoolean("streak", true))
            .putString("days", days.joinToString(","))
            .apply()
        schedule(context)
    }

    /**
     * Alle Erinnerungen neu stellen. policy: REPLACE nach einer Änderung in den Einstellungen (neue
     * Uhrzeit gilt sofort), KEEP beim App-Start (nur fehlende anlegen), APPEND_OR_REPLACE aus dem
     * laufenden Worker (die nächste startet, wenn diese fertig ist — ohne sich selbst abzubrechen).
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE, only: String? = null) {
        val c = read(context)
        val wm = WorkManager.getInstance(context)
        OLD_PERIODIC.forEach { wm.cancelUniqueWork(it) }
        val kinds = listOfNotNull("train", if (c.streak) "streak" else null, "week")
        if (!c.enabled) {
            listOf("train", "streak", "week").forEach { wm.cancelUniqueWork(work(it)) }
            return
        }
        if (!c.streak) wm.cancelUniqueWork(work("streak"))
        for (kind in kinds) {
            if (only != null && kind != only) continue
            val delay = when (kind) {
                "train" -> delayUntil(c.minutes)
                "streak" -> delayUntil(STREAK_MIN)
                else -> delayUntilMonday(9 * 60)
            }
            wm.enqueueUniqueWork(
                work(kind), policy,
                OneTimeWorkRequestBuilder<FitReminderWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("kind" to kind)).build(),
            )
        }
    }

    internal fun delayUntil(minutes: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }

    internal fun delayUntilMonday(minutes: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (!next.after(now)) next.add(Calendar.WEEK_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }

    /** 0 = Montag … 6 = Sonntag (wie im Fitness-Profil). */
    fun todayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@HiltWorker
class FitReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: "train"
        try {
            remind(kind)
        } finally {
            // Nächste Erinnerung dieser Art auf die nächste echte Uhrzeit stellen.
            runCatching { FitReminders.schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE, only = kind) }
        }
        return Result.success()
    }

    private suspend fun remind(kind: String) {
        val c = FitReminders.read(context)
        if (kind == "week") { weekly(c); return }
        if (!c.enabled || FitReminders.todayIndex() !in c.days) return
        // Stark verspätet (Gerät lange im Tiefschlaf)? Dann lieber keine Erinnerung als eine falsche.
        val now = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        val due = if (kind == "streak") FitReminders.STREAK_MIN else c.minutes
        if (now < due - 5 || now > due + 180) return
        // Offline gespeicherte Trainings zuerst nachsenden — sonst mahnt »Serie retten« ein erledigtes Training an.
        withContext(Dispatchers.IO) { runCatching { FitCache.flush(context, okHttp) } }
        // Was steht heute an? (Server über den App-Client — Host wird umgeschrieben.)
        val today = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newBuilder().callTimeout(java.time.Duration.ofSeconds(12)).build()
                    .newCall(Request.Builder().url("http://hikari.invalid/fitness/today").build()).execute()
                    .use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
            }.getOrNull()
        }
        if (today?.optString("status") == "done") return
        val title = today?.optJSONObject("plan")?.optString("title").orEmpty()
        val minutes = today?.optJSONArray("blockSeconds")?.let { a -> (0 until a.length()).sumOf { a.optInt(it) } / 60 } ?: 0
        when (kind) {
            "streak" -> if (today != null) notify(
                "Noch Zeit für 15 Minuten?",
                "Heute ist Trainingstag — ein kurzes Training hält deine Serie am Leben.",
            )
            else -> notify(
                "Heute ist Trainingstag",
                if (title.isNotBlank()) "$title${if (minutes > 0) " · $minutes min" else ""} — dein Coach hat alles vorbereitet."
                else "Dein Coach hat dein Training vorbereitet.",
            )
        }
    }

    private suspend fun weekly(c: FitReminders.Config) {
        if (!c.enabled) return
        val week = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newBuilder().callTimeout(java.time.Duration.ofSeconds(12)).build()
                    .newCall(Request.Builder().url("http://hikari.invalid/fitness/week").build()).execute()
                    .use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
            }.getOrNull()
        } ?: return
        val n = week.optJSONObject("review")?.optInt("sessions") ?: 0
        if (n > 0) notify("Dein Wochenrückblick ist bereit", "$n ${if (n == 1) "Einheit" else "Einheiten"} letzte Woche — dein Coach sagt dir, was diese Woche kommt.")
    }

    private fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(FitReminders.CHANNEL_ID, "Training", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Erinnerung an Trainingstagen und abends, wenn noch nicht trainiert wurde."
            },
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "fitness")
        }
        val pending = PendingIntent.getActivity(context, 4401, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(
            4401,
            NotificationCompat.Builder(context, FitReminders.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pending).build(),
        )
    }
}
