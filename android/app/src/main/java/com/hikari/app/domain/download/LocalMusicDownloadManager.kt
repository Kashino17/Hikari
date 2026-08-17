package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.di.MusicDownloadClient
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lädt Songs als Audiodatei auf das Gerät. Analog zu
 * [LocalDownloadManager]: eine DB-Zeile entsteht erst, wenn die Datei
 * vollständig ist; der Fortschritt lebt nur im Speicher.
 *
 * Die Stream-URL kommt vom Repository (Backend/yt-dlp mit Piped-Fallback) und
 * ist kurzlebig — sie wird deshalb direkt vor dem Download aufgelöst.
 */
@Singleton
class LocalMusicDownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: LocalMusicDownloadDao,
    private val repo: MusicRepository,
    @MusicDownloadClient private val client: OkHttpClient,
) {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** Abbruch-Wünsche; die Download-Schleife prüft das Set pro Chunk. */
    private val cancelRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Bricht einen laufenden (oder wartenden) Download ab. */
    fun cancel(videoId: String) {
        if (videoId in _progress.value) cancelRequests.add(videoId)
    }

    /** Bricht alle laufenden und wartenden Downloads ab. */
    fun cancelAll() {
        cancelRequests.addAll(_progress.value.keys)
    }

    private class DownloadCancelled : Exception("Abgebrochen")

    val downloadedIds: Flow<List<String>> = dao.observeIds()
    val downloads: Flow<List<LocalMusicDownloadEntity>> = dao.observeAll()

    /** Zwei parallele Downloads — genug Tempo, ohne die Leitung dichtzumachen. */
    private val slots = Semaphore(2)

    private val musicDir: File by lazy { File(ctx.filesDir, "music").apply { mkdirs() } }

    suspend fun download(song: MusicSong): Result<LocalMusicDownloadEntity> =
        withContext(Dispatchers.IO) {
            if (isDownloaded(song.videoId)) {
                return@withContext dao.get(song.videoId)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("Download-Eintrag verschwunden"))
            }
            // Platzhalter sofort setzen, damit die UI reagiert, bevor die
            // (langsame) yt-dlp-Auflösung überhaupt startet.
            _progress.put(song.videoId, 0f)
            slots.withPermit {
                val target = File(musicDir, "${song.videoId}.m4a")
                try {
                    // Abbruch, der schon im Warteslot ankam
                    if (song.videoId in cancelRequests) throw DownloadCancelled()
                    val url = repo.getAudioStream(song.videoId)
                        ?: return@withPermit fail(song.videoId, target, "Kein Audio-Stream gefunden")
                    if (song.videoId in cancelRequests) throw DownloadCancelled()

                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return@withPermit fail(song.videoId, target, "HTTP ${resp.code}")
                        }
                        val body = resp.body
                            ?: return@withPermit fail(song.videoId, target, "Leere Antwort")
                        val totalBytes = body.contentLength()
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                var written = 0L
                                while (true) {
                                    if (song.videoId in cancelRequests) throw DownloadCancelled()
                                    val n = input.read(buf)
                                    if (n == -1) break
                                    output.write(buf, 0, n)
                                    written += n
                                    if (totalBytes > 0) {
                                        _progress.put(
                                            song.videoId,
                                            (written.toFloat() / totalBytes).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (target.length() == 0L) {
                        return@withPermit fail(song.videoId, target, "Datei ist leer")
                    }

                    val entity = LocalMusicDownloadEntity(
                        videoId = song.videoId,
                        localFilePath = target.absolutePath,
                        byteSize = target.length(),
                        downloadedAt = System.currentTimeMillis(),
                        title = song.title,
                        uploader = song.uploader,
                        thumbnailUrl = song.thumbnailUrl,
                        durationSeconds = song.duration,
                    )
                    dao.upsert(entity)
                    // Song auch in der Bibliothek kennen (Favoriten/Playlists
                    // referenzieren music_songs per Fremdschlüssel).
                    runCatching { repo.recordPlayed(song, touchRecency = false) }
                    cancelRequests.remove(song.videoId)
                    _progress.remove(song.videoId)
                    Result.success(entity)
                } catch (e: Exception) {
                    target.delete()
                    cancelRequests.remove(song.videoId)
                    _progress.remove(song.videoId)
                    Result.failure(e)
                }
            }
        }

    private fun fail(videoId: String, target: File, message: String): Result<LocalMusicDownloadEntity> {
        target.delete()
        cancelRequests.remove(videoId)
        _progress.remove(videoId)
        return Result.failure(IllegalStateException(message))
    }

    suspend fun isDownloaded(videoId: String): Boolean {
        val row = dao.get(videoId) ?: return false
        return File(row.localFilePath).exists()
    }

    /** Lokale Datei oder null; räumt dabei verwaiste DB-Zeilen selbst auf. */
    suspend fun localFile(videoId: String): File? {
        val row = dao.get(videoId) ?: return null
        val file = File(row.localFilePath)
        if (!file.exists()) {
            dao.delete(videoId)
            return null
        }
        return file
    }

    suspend fun delete(videoId: String) {
        dao.get(videoId)?.let { File(it.localFilePath).delete() }
        dao.delete(videoId)
        _progress.remove(videoId)
    }

    suspend fun deleteAll() {
        dao.getAll().forEach { File(it.localFilePath).delete() }
        dao.getAll().forEach { dao.delete(it.videoId) }
    }

    private fun MutableStateFlow<Map<String, Float>>.put(id: String, value: Float) {
        this.value = this.value + (id to value)
    }

    private fun MutableStateFlow<Map<String, Float>>.remove(id: String) {
        this.value = this.value - id
    }
}
