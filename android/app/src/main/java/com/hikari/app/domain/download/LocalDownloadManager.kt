package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pulls a video file from the backend onto the device's app-private storage
 * (`filesDir/downloads/<videoId>.mp4`) and tracks it in Room. Survives screen
 * rotation but not process death — for v0.23.0 we don't run a foreground
 * service. Smart-Downloads engine (v0.24.0+) will move this to WorkManager.
 */
@Singleton
class LocalDownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: LocalDownloadDao,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    /** Per-videoId progress 0f..1f. Removed from map when download finishes. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** Set of locally-stored videoIds, observed by UI for the "downloaded" badge. */
    val downloadedIds: Flow<List<String>> = dao.observeIds()

    private val downloadsDir: File by lazy {
        File(ctx.filesDir, "downloads").apply { mkdirs() }
    }

    suspend fun isDownloaded(videoId: String): Boolean {
        val entity = dao.get(videoId) ?: return false
        return File(entity.localFilePath).exists()
    }

    suspend fun localFile(videoId: String): File? {
        val entity = dao.get(videoId) ?: return null
        val file = File(entity.localFilePath)
        return if (file.exists()) file else null.also {
            // DB row stale → cleanup
            dao.delete(videoId)
        }
    }

    suspend fun delete(videoId: String) = withContext(Dispatchers.IO) {
        val entity = dao.get(videoId)
        if (entity != null) {
            File(entity.localFilePath).delete()
            dao.delete(videoId)
        }
    }

    /**
     * Downloads `videos/<videoId>.mp4` from the configured backend into local
     * storage. Returns Result.success on completion, Result.failure on any
     * IO/HTTP error (also clears progress entry).
     */
    suspend fun download(videoId: String, durationSeconds: Int = 0): Result<LocalDownloadEntity> =
        withContext(Dispatchers.IO) {
            val target = File(downloadsDir, "$videoId.mp4")
            try {
                _progress.update(videoId, 0f)
                val backend = settings.backendUrl.first().trimEnd('/')
                val req = Request.Builder()
                    .url("$backend/videos/$videoId.mp4")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _progress.remove(videoId)
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${resp.code}: ${resp.message}"),
                        )
                    }
                    val body = resp.body ?: return@withContext Result.failure(
                        IllegalStateException("empty body").also { _progress.remove(videoId) },
                    )
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                output.write(buf, 0, n)
                                written += n
                                if (totalBytes > 0) {
                                    _progress.update(
                                        videoId,
                                        (written.toFloat() / totalBytes).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    }
                }

                val entity = LocalDownloadEntity(
                    videoId = videoId,
                    localFilePath = target.absolutePath,
                    byteSize = target.length(),
                    downloadedAt = System.currentTimeMillis(),
                    durationSeconds = durationSeconds,
                )
                dao.upsert(entity)
                _progress.remove(videoId)
                Result.success(entity)
            } catch (e: Exception) {
                target.delete()
                _progress.remove(videoId)
                Result.failure(e)
            }
        }

    private fun MutableStateFlow<Map<String, Float>>.update(id: String, value: Float) {
        this.value = this.value + (id to value)
    }

    private fun MutableStateFlow<Map<String, Float>>.remove(id: String) {
        this.value = this.value - id
    }
}
