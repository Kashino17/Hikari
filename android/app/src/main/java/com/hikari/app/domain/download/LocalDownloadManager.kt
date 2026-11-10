package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadKind
import com.hikari.app.data.db.LocalDownloadSource
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reichhaltige Metadaten, die beim Download mitgeschrieben werden, damit der
 * Profile-Tab offline ohne Server vollständig rendern kann.
 *
 * Pflichtfelder: videoId, kind, title, durationSeconds.
 * Alles andere ist optional und wird je nach kind gefüllt:
 *   - SERIES → seriesId/Title/Thumbnail + season/episode
 *   - CHANNEL → channelId/Title/Thumbnail
 *   - MOVIE → kein Gruppen-Bezug
 */
data class LocalDownloadMetadata(
    val videoId: String,
    val kind: LocalDownloadKind,
    val title: String,
    val durationSeconds: Int,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val channelThumbnailUrl: String? = null,
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val seriesThumbnailUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Selbst angestoßen oder von den Smart-Downloads geholt. */
    val source: LocalDownloadSource = LocalDownloadSource.MANUAL,
    /** Echte Plakate, soweit der Aufrufer sie kennt — sonst trägt OfflineArtworkSync sie nach. */
    val posterUrl: String? = null,
    val seriesPosterUrl: String? = null,
    val seriesBackdropUrl: String? = null,
)

/**
 * Pulls a video file from the backend onto the device's app-private storage
 * (`filesDir/downloads/<videoId>.mp4`) and tracks it in Room.
 *
 * Die Übertragung läuft in einem eigenen, app-weiten Scope: wer den Bildschirm
 * verlässt (ViewModel wird beendet), bricht den Download damit nicht mehr ab.
 * Den Prozess-Tod überlebt er nicht — dafür gibt es keinen Vordergrund-Dienst.
 */
@Singleton
class LocalDownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: LocalDownloadDao,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Laufende Übertragungen — ein zweiter Aufruf fürs selbe Video hängt sich an. */
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<LocalDownloadEntity>>>()

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

    /** Was über einen Download gemerkt ist — für Ansichten, die ohne Server auskommen müssen. */
    suspend fun entity(videoId: String): LocalDownloadEntity? = dao.get(videoId)

    /** Alle Folgen einer Serie, die auf dem Gerät liegen, in Sendereihenfolge. */
    suspend fun episodesOf(seriesId: String): List<LocalDownloadEntity> =
        dao.all()
            .filter { it.seriesId == seriesId }
            .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))

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
     *
     * Wird der Aufrufer abgebrochen, läuft die Übertragung trotzdem zu Ende.
     */
    suspend fun download(meta: LocalDownloadMetadata): Result<LocalDownloadEntity> {
        val job = inFlight.computeIfAbsent(meta.videoId) { id ->
            scope.async {
                try {
                    transfer(meta)
                } finally {
                    inFlight.remove(id)
                }
            }
        }
        return job.await()
    }

    /**
     * Lädt mehrere Videos nacheinander (z. B. eine ganze Staffel) und hört beim
     * ersten Fehler auf. Schon vorhandene werden übersprungen. Auch das läuft
     * weiter, wenn der Aufrufer den Bildschirm verlässt.
     */
    suspend fun downloadAll(metas: List<LocalDownloadMetadata>): Result<Unit> =
        scope.async {
            for (meta in metas) {
                if (isDownloaded(meta.videoId)) continue
                val result = download(meta)
                if (result.isFailure) return@async Result.failure<Unit>(result.exceptionOrNull() ?: IllegalStateException())
            }
            Result.success(Unit)
        }.await()

    private suspend fun transfer(meta: LocalDownloadMetadata): Result<LocalDownloadEntity> =
        withContext(Dispatchers.IO) {
            val target = File(downloadsDir, "${meta.videoId}.mp4")
            try {
                _progress.update(meta.videoId, 0f)
                val backend = settings.backendUrl.first().trimEnd('/')
                val req = Request.Builder()
                    .url("$backend/videos/${meta.videoId}.mp4")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _progress.remove(meta.videoId)
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${resp.code}: ${resp.message}"),
                        )
                    }
                    val body = resp.body ?: return@withContext Result.failure(
                        IllegalStateException("empty body").also { _progress.remove(meta.videoId) },
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
                                        meta.videoId,
                                        (written.toFloat() / totalBytes).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    }
                }

                // Ein erneuter Download kennt die Plakate oft nicht — die schon gemerkten bleiben.
                val known = dao.get(meta.videoId)
                val entity = LocalDownloadEntity(
                    videoId = meta.videoId,
                    localFilePath = target.absolutePath,
                    byteSize = target.length(),
                    downloadedAt = System.currentTimeMillis(),
                    durationSeconds = meta.durationSeconds,
                    kind = meta.kind.name,
                    title = meta.title,
                    thumbnailUrl = meta.thumbnailUrl,
                    channelId = meta.channelId,
                    channelTitle = meta.channelTitle,
                    channelThumbnailUrl = meta.channelThumbnailUrl,
                    seriesId = meta.seriesId,
                    seriesTitle = meta.seriesTitle,
                    seriesThumbnailUrl = meta.seriesThumbnailUrl,
                    season = meta.season,
                    episode = meta.episode,
                    source = meta.source.name,
                    posterUrl = meta.posterUrl ?: known?.posterUrl,
                    seriesPosterUrl = meta.seriesPosterUrl ?: known?.seriesPosterUrl,
                    seriesBackdropUrl = meta.seriesBackdropUrl ?: known?.seriesBackdropUrl,
                )
                dao.upsert(entity)
                _progress.remove(meta.videoId)
                Result.success(entity)
            } catch (e: Exception) {
                target.delete()
                _progress.remove(meta.videoId)
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
