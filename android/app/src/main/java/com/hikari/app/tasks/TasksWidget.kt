package com.hikari.app.tasks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
 * Widget auf dem Startbildschirm: was gerade in 15 Minuten passt, Haken direkt im Widget,
 * Mikrofon für Neues. Der letzte Stand liegt in den Prefs, damit es ohne Server nicht leer ist.
 */
class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        draw(ctx) // sofort mit dem letzten Stand
        refresh(ctx)
    }

    companion object {
        private const val PREFS = "hikari_tasks_widget"

        fun refresh(ctx: Context) {
            if (AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, TasksWidget::class.java)).isEmpty()) return
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "tasks-widget", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TasksWidgetWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }

        fun save(ctx: Context, json: String) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply()

        /** Abgehakt im Widget: sofort ausblenden, der Server zieht nach. */
        fun markDone(ctx: Context, id: String) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit().putString("hidden", id).apply()
            draw(ctx)
        }

        fun draw(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TasksWidget::class.java))
            if (ids.isEmpty()) return
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val data = runCatching { JSONObject(p.getString("data", null) ?: "{}") }.getOrDefault(JSONObject())
            val items = data.optJSONArray("items")
            val hidden = p.getString("hidden", null)
            val top = (0 until (items?.length() ?: 0)).map { items!!.getJSONObject(it) }.firstOrNull { it.optString("id") != hidden }
            val open = (data.optInt("open", 0) - if (hidden != null && top == null) 1 else 0).coerceAtLeast(0)

            val v = RemoteViews(ctx.packageName, R.layout.widget_tasks)
            v.setTextViewText(R.id.wTitle, top?.optString("title") ?: if (data.has("open")) "Gerade nichts, das in 15 Minuten passt" else "Tipp aufs Mikrofon und sag, was ansteht")
            v.setTextViewText(R.id.wMeta, if (open > 0) "$open offen" else "")
            v.setTextViewText(R.id.wMin, top?.let { "${it.optInt("minutes")} min" } ?: "15 min")
            if (top != null) {
                v.setViewVisibility(R.id.wDone, View.VISIBLE)
                v.setOnClickPendingIntent(
                    R.id.wDone,
                    PendingIntent.getBroadcast(
                        ctx, 7900,
                        Intent(ctx, TaskActionReceiver::class.java).setAction(TaskActionReceiver.DONE).putExtra("id", top.optString("id")),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                v.setOnClickPendingIntent(R.id.wTitle, TaskNotifications.openApp(ctx, "tasks?page=focus:${top.optString("id")}", 7901))
            } else {
                v.setViewVisibility(R.id.wDone, View.GONE)
                v.setOnClickPendingIntent(R.id.wTitle, TaskNotifications.openApp(ctx, "profile?tab=aufgaben", 7901))
            }
            v.setOnClickPendingIntent(R.id.wHead, TaskNotifications.openApp(ctx, "profile?tab=aufgaben", 7902))
            v.setOnClickPendingIntent(
                R.id.wMic,
                PendingIntent.getActivity(
                    ctx, 7903,
                    Intent(ctx, TaskCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            mgr.updateAppWidget(ids, v)
        }
    }
}

@HiltWorker
class TasksWidgetWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val json = runCatching {
            okHttp.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url("${TaskCaptureWorker.BASE}/tasks/briefing?minutes=15&max=1").build()).execute()
                .use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
        if (json != null) {
            TasksWidget.save(ctx, json)
            ctx.getSharedPreferences("hikari_tasks_widget", Context.MODE_PRIVATE).edit().remove("hidden").apply()
        }
        TasksWidget.draw(ctx)
        Result.success()
    }
}

/** Briefing um 7:30: die wichtigsten Dinge des Tages, zusammen höchstens 90 Minuten. */
object TaskBriefing {
    private const val WORK = "tasks-briefing"
    const val AT_MIN = 7 * 60 + 30

    fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, AT_MIN / 60); set(Calendar.MINUTE, AT_MIN % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK, policy,
            OneTimeWorkRequestBuilder<TaskBriefingWorker>()
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}

@HiltWorker
class TaskBriefingWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val c = Calendar.getInstance()
            val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            // Stark verspätet (Gerät lange im Tiefschlaf)? Dann lieber kein Morgen-Briefing am Mittag.
            if (now in (TaskBriefing.AT_MIN - 10)..(TaskBriefing.AT_MIN + 150)) {
                val o = withContext(Dispatchers.IO) {
                    runCatching {
                        okHttp.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
                            .newCall(Request.Builder().url("${TaskCaptureWorker.BASE}/tasks/briefing").build()).execute()
                            .use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
                    }.getOrNull()
                }
                val items = o?.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    TaskNotifications.briefing(ctx, (0 until items.length()).map { items.getJSONObject(it) }, o.optInt("minutes"))
                }
                TasksWidget.refresh(ctx)
            }
        } finally {
            TaskBriefing.schedule(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
