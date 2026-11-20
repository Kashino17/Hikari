package com.hikari.app.tasks

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Aufgaben eintragen, ohne zu warten: Aufnahme oder Text geht an den Hikari-Server
 * (Whisper schreibt mit, Qwen zerlegt in Aufgaben), das Ergebnis kommt als Benachrichtigung.
 * Ohne Netz wartet die Arbeit, bis der Mac erreichbar ist; die Tondatei bleibt so lange liegen.
 */
object TaskCapture {
    /** Meldet der offenen Aufgaben-Oberfläche, dass sich etwas geändert hat. */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val changes: SharedFlow<Unit> = _changes
    fun notifyChanged(ctx: Context) {
        _changes.tryEmit(Unit)
        TasksWidget.refresh(ctx)
    }

    fun audioDir(ctx: Context) = File(ctx.filesDir, "task-captures").apply { mkdirs() }

    fun enqueueAudio(ctx: Context, file: File) = enqueue(ctx, workDataOf(TaskCaptureWorker.KEY_FILE to file.absolutePath, TaskCaptureWorker.KEY_SOURCE to "voice"))

    fun enqueueText(ctx: Context, text: String, source: String) =
        enqueue(ctx, workDataOf(TaskCaptureWorker.KEY_TEXT to text.take(4000), TaskCaptureWorker.KEY_SOURCE to source))

    private fun enqueue(ctx: Context, data: androidx.work.Data) {
        WorkManager.getInstance(ctx).enqueue(
            OneTimeWorkRequestBuilder<TaskCaptureWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TaskCaptureWorker.TAG)
                .build(),
        )
    }
}

@HiltWorker
class TaskCaptureWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = TaskNotifications.working(ctx)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(TaskNotifications.WORKING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(TaskNotifications.WORKING_ID, n)
        }
    }

    private val http by lazy { okHttp.newBuilder().callTimeout(90, TimeUnit.SECONDS).build() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_CAPTURE) ?: upload() ?: return@withContext lastResult
        await(id)
    }

    private var lastResult: Result = Result.failure()

    private fun upload(): String? {
        val path = inputData.getString(KEY_FILE)
        val text = inputData.getString(KEY_TEXT)
        val source = inputData.getString(KEY_SOURCE) ?: "voice"
        val body = if (path != null) {
            val f = File(path)
            if (!f.exists() || f.length() == 0L) { lastResult = Result.failure(); return null }
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("source", source)
                .addFormDataPart("audio", f.name, f.asRequestBody("audio/mp4".toMediaType()))
                .build()
        } else {
            JSONObject().put("text", text.orEmpty()).put("source", source).toString()
                .toRequestBody("application/json".toMediaType())
        }
        return try {
            http.newCall(Request.Builder().url("$BASE/tasks/capture").post(body).build()).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        path?.let { File(it).delete() }
                        TaskCapture.notifyChanged(ctx) // »Hikari sortiert …« in der Oberfläche
                        JSONObject(r.body?.string().orEmpty()).getString("id")
                    }
                    r.code in 400..499 -> {
                        path?.let { File(it).delete() }
                        TaskNotifications.failed(ctx, "Der Server hat die Aufnahme abgelehnt (${r.code}).")
                        lastResult = Result.failure(); null
                    }
                    else -> { lastResult = retry(path); null }
                }
            }
        } catch (_: IOException) {
            lastResult = retry(path); null
        }
    }

    private fun retry(path: String?): Result {
        if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        // Aufnahme nicht wegwerfen: Kadir soll sie nicht neu sprechen müssen.
        TaskNotifications.failed(ctx, "Hikari-Server war nicht erreichbar. Die Aufnahme ist gespeichert und geht beim nächsten Mal mit.")
        path?.let { TaskCapture.enqueueAudio(ctx, File(it)) }
        return Result.failure()
    }

    private suspend fun await(id: String): Result {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < WINDOW_MS) {
            val o = try {
                http.newCall(Request.Builder().url("$BASE/tasks/capture/$id").build()).execute().use { r ->
                    if (r.code == 404) return Result.success()
                    if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null
                }
            } catch (_: IOException) { null }
            when (o?.optString("status")) {
                "done" -> {
                    val created = o.optJSONArray("created")
                    val items = (0 until (created?.length() ?: 0)).map { created!!.getJSONObject(it) }
                    if (items.isEmpty()) TaskNotifications.failed(ctx, "Hikari hat in der Aufnahme keine Aufgabe gefunden.")
                    else TaskNotifications.result(ctx, id, items, o.optBoolean("fallback"))
                    TaskCapture.notifyChanged(ctx)
                    return Result.success()
                }
                "failed" -> {
                    TaskNotifications.failed(ctx, "Hikari hat die Aufnahme nicht verstanden.")
                    TaskCapture.notifyChanged(ctx)
                    return Result.success()
                }
            }
            delay(POLL_MS)
        }
        // Noch nicht fertig (Modell lädt, Server beschäftigt): mit derselben ID weiter warten.
        WorkManager.getInstance(ctx).enqueue(
            OneTimeWorkRequestBuilder<TaskCaptureWorker>()
                .setInputData(workDataOf(KEY_CAPTURE to id))
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag(TAG).build(),
        )
        return Result.success()
    }

    companion object {
        const val TAG = "task-capture"
        const val KEY_FILE = "file"
        const val KEY_TEXT = "text"
        const val KEY_SOURCE = "source"
        const val KEY_CAPTURE = "capture"
        /** Platzhalter-Host: der App-Client schreibt ihn auf die eingestellte Server-Adresse um. */
        const val BASE = "http://hikari.invalid"
        private const val POLL_MS = 2_500L
        private const val WINDOW_MS = 8 * 60_000L
        private const val MAX_ATTEMPTS = 6
    }
}

/** Kleine Aktionen aus Benachrichtigung und Widget (Rückgängig, Abhaken). */
@HiltWorker
class TaskActionWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val req = when (inputData.getString("action")) {
            ACTION_UNDO -> Request.Builder().url("${TaskCaptureWorker.BASE}/tasks/capture/$id/undo").post("{}".toRequestBody("application/json".toMediaType()))
            ACTION_DONE -> Request.Builder().url("${TaskCaptureWorker.BASE}/tasks/$id")
                .patch("""{"done":true}""".toRequestBody("application/json".toMediaType()))
            else -> return@withContext Result.failure()
        }
        try {
            okHttp.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(req.build()).execute().use { r ->
                if (!r.isSuccessful && r.code >= 500) return@withContext Result.retry()
            }
        } catch (_: IOException) {
            return@withContext if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
        TaskCapture.notifyChanged(ctx)
        Result.success()
    }

    companion object {
        const val ACTION_UNDO = "undo"
        const val ACTION_DONE = "done"
        fun enqueue(ctx: Context, action: String, id: String) {
            WorkManager.getInstance(ctx).enqueue(
                OneTimeWorkRequestBuilder<TaskActionWorker>()
                    .setInputData(workDataOf("action" to action, "id" to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
