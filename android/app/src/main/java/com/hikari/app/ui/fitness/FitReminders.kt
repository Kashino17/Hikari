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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
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
 * Trainings-Erinnerungen: an Trainingstagen zur gewählten Uhrzeit (»Heute ist Trainingstag«) und
 * abends um 20:30, wenn noch nicht trainiert wurde (»Serie retten«). Einstellungen setzt die
 * Fitness-Oberfläche über die Brücke (FitWebBridge.setReminders), gespeichert in SharedPreferences.
 */
object FitReminders {
    private const val PREFS = "hikari_fit_reminders"
    private const val WORK_TRAIN = "fit-reminder-train"
    private const val WORK_STREAK = "fit-reminder-streak"
    private const val WORK_WEEK = "fit-reminder-week"
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

    fun schedule(context: Context) {
        val c = read(context)
        val wm = WorkManager.getInstance(context)
        if (!c.enabled) {
            wm.cancelUniqueWork(WORK_TRAIN)
            wm.cancelUniqueWork(WORK_STREAK)
            wm.cancelUniqueWork(WORK_WEEK)
            return
        }
        // Montags 9 Uhr: Wochenrückblick (nur wenn letzte Woche trainiert wurde — prüft der Worker).
        wm.enqueueUniquePeriodicWork(
            WORK_WEEK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<FitReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilMonday(9 * 60), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("kind" to "week")).build(),
        )
        wm.enqueueUniquePeriodicWork(
            WORK_TRAIN, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<FitReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(c.minutes), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("kind" to "train")).build(),
        )
        if (c.streak) {
            wm.enqueueUniquePeriodicWork(
                WORK_STREAK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<FitReminderWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delayUntil(20 * 60 + 30), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("kind" to "streak")).build(),
            )
        } else wm.cancelUniqueWork(WORK_STREAK)
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
}

@HiltWorker
class FitReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = FitReminders.read(context)
        val kind = inputData.getString("kind") ?: "train"
        if (kind == "week") return weekly(c)
        if (!c.enabled || FitReminders.todayIndex() !in c.days) return Result.success()
        // Was steht heute an? (Server über den App-Client — Host wird umgeschrieben.)
        val today = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newBuilder().callTimeout(java.time.Duration.ofSeconds(12)).build()
                    .newCall(Request.Builder().url("http://hikari.invalid/fitness/today").build()).execute()
                    .use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
            }.getOrNull()
        }
        if (today?.optString("status") == "done") return Result.success()
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
        return Result.success()
    }

    private suspend fun weekly(c: FitReminders.Config): Result {
        if (!c.enabled) return Result.success()
        val week = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newBuilder().callTimeout(java.time.Duration.ofSeconds(12)).build()
                    .newCall(Request.Builder().url("http://hikari.invalid/fitness/week").build()).execute()
                    .use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
            }.getOrNull()
        } ?: return Result.success()
        val n = week.optJSONObject("review")?.optInt("sessions") ?: 0
        if (n > 0) notify("Dein Wochenrückblick ist bereit", "$n ${if (n == 1) "Einheit" else "Einheiten"} letzte Woche — dein Coach sagt dir, was diese Woche kommt.")
        return Result.success()
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
