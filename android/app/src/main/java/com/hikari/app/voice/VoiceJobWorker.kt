package com.hikari.app.voice

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VoiceJobDto
import com.hikari.app.domain.voice.VoiceFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Lädt einen Batch Sprachnachrichten hoch und wartet auf die Zusammenfassung.
 * Fortschritt und Ergebnis kommen als Benachrichtigung. Dauert es länger als
 * ein Worker-Fenster, übernimmt eine Fortsetzung mit der Job-ID.
 */
@HiltWorker
class VoiceJobWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val api: HikariApi,
) : CoroutineWorker(ctx, params) {

    private val batchPath = inputData.getString(KEY_BATCH)
    private val key = inputData.getString(KEY_NOTIF) ?: batchPath ?: inputData.getString(KEY_JOB) ?: "voice"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = VoiceNotifications.progress(ctx, key, "Sprachnachrichten werden hochgeladen", null)
        val id = VoiceNotifications.idFor(key)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, n)
        }
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: (upload() ?: return lastResult)
        return awaitResult(jobId)
    }

    private var lastResult: Result = Result.failure()

    /** Gibt die Job-ID zurück; bei null steht das Ergebnis in [lastResult]. */
    private suspend fun upload(): String? {
        val dir = batchPath?.let(::File)
        val files = dir?.let(VoiceIntake::batchFiles).orEmpty()
        if (dir == null || files.isEmpty()) {
            lastResult = Result.failure()
            return null
        }
        VoiceNotifications.showProgress(
            ctx,
            key,
            if (files.size == 1) "Sprachnachricht wird hochgeladen" else "${files.size} Sprachnachrichten werden hochgeladen",
            null,
        )
        return try {
            val parts = files.map { (file, name) ->
                MultipartBody.Part.createFormData("audio", name, file.asRequestBody(mimeFor(name).toMediaType()))
            }
            val label = inputData.getString(KEY_LABEL)?.toRequestBody("text/plain".toMediaType())
            val job = api.createVoiceJob(label, parts)
            dir.deleteRecursively()
            job.id
        } catch (e: HttpException) {
            // 4xx: wird nie klappen — Batch weg, Bescheid geben.
            if (e.code() in 400..499) {
                dir.deleteRecursively()
                VoiceNotifications.showUploadFailed(ctx, key, "Der Server hat die Dateien abgelehnt (${e.code()}).")
                lastResult = Result.failure()
            } else {
                lastResult = retryOrGiveUp(dir)
            }
            null
        } catch (_: IOException) {
            lastResult = retryOrGiveUp(dir)
            null
        }
    }

    private fun retryOrGiveUp(dir: File): Result {
        if (runAttemptCount < MAX_UPLOAD_ATTEMPTS) {
            VoiceNotifications.showProgress(ctx, key, "Hikari-Server nicht erreichbar, neuer Versuch gleich", null)
            return Result.retry()
        }
        dir.deleteRecursively()
        VoiceNotifications.showUploadFailed(ctx, key, "Der Hikari-Server war nicht erreichbar.")
        return Result.failure()
    }

    private suspend fun awaitResult(jobId: String): Result {
        val started = System.currentTimeMillis()
        var lastOk = started
        while (System.currentTimeMillis() - started < WINDOW_MS) {
            val job: VoiceJobDto? = try {
                api.getVoiceJob(jobId).also { lastOk = System.currentTimeMillis() }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    VoiceNotifications.cancel(ctx, key)
                    return Result.success() // in der App gelöscht
                }
                null
            } catch (_: IOException) {
                null
            }
            if (job != null) {
                if (VoiceFormat.isFinished(job.status)) {
                    VoiceNotifications.showResult(ctx, key, job)
                    return Result.success()
                }
                VoiceNotifications.showProgress(
                    ctx,
                    key,
                    VoiceFormat.statusLine(job.status, job.done, job.total, job.queuePosition),
                    VoiceFormat.progress(job.status, job.done, job.total),
                )
            } else if (System.currentTimeMillis() - lastOk > 3 * 60_000) {
                break // Server länger weg: später weiter
            }
            delay(POLL_MS)
        }
        // Noch nicht fertig: Fortsetzung mit derselben Benachrichtigung.
        val round = inputData.getInt(KEY_ROUND, 0) + 1
        if (round > MAX_ROUNDS) {
            VoiceNotifications.showUploadFailed(ctx, key, "Dauert ungewöhnlich lange — schau in Hikari nach.")
            return Result.success()
        }
        WorkManager.getInstance(ctx).enqueue(
            OneTimeWorkRequestBuilder<VoiceJobWorker>()
                .setInputData(workDataOf(KEY_JOB to jobId, KEY_NOTIF to key, KEY_ROUND to round))
                .addTag(TAG)
                .build(),
        )
        return Result.success()
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "opus", "ogg", "oga" -> "audio/ogg"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "amr" -> "audio/amr"
        else -> "application/octet-stream"
    }

    companion object {
        const val TAG = "voice-job"
        const val KEY_BATCH = "batch"
        const val KEY_JOB = "job"
        const val KEY_LABEL = "label"
        const val KEY_NOTIF = "notif"
        const val KEY_ROUND = "round"
        private const val POLL_MS = 2_000L
        /** Unter dem 10-Minuten-Limit eines Workers bleiben. */
        private const val WINDOW_MS = 8 * 60_000L
        private const val MAX_ROUNDS = 8
        private const val MAX_UPLOAD_ATTEMPTS = 5
    }
}
