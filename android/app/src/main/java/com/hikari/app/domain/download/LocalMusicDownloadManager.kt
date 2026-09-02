package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.db.LocalMusicDownloadEntity
import com.hikari.app.di.MusicDownloadClient
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.domain.repo.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lädt Songs als Audiodatei auf das Gerät. Analog zu
 * [LocalDownloadManager]: eine DB-Zeile entsteht erst, wenn die Datei
 * vollständig ist; der Fortschritt lebt nur im Speicher.
 *
 * Seit v0.79 läuft der eigentliche YouTube-Download auf dem Server: Die App
 * reiht den Song dort ein (`POST /music/download/:id`), fragt den Stand ab
 * und holt am Ende die fertige Datei von der Server-Platte — derselbe Weg,
 * über den Serien und Filme aufs Gerät kommen. Vorher lud die App live durch
 * den Proxy, und googlevideo sperrte die Server-IP nach ~9 Songs mit 403;
 * der Rest der Playlist scheiterte dann binnen Sekunden. Der Server wartet
 * solche Wellen jetzt aus, die App bleibt geduldig dran.
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

    /**
     * Hinweis, solange der Server wegen einer YouTube-Drossel wartet — null,
     * wenn alles normal läuft. Ohne ihn sähe ein wartender Download wie ein
     * Hänger aus.
     */
    private val _throttleNotice = MutableStateFlow<String?>(null)
    val throttleNotice: StateFlow<String?> = _throttleNotice.asStateFlow()

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

    /** Der Server hat den Song endgültig aufgegeben (oder ist nicht erreichbar). */
    private class ServerDownloadFailed(message: String) : Exception(message)

    val downloadedIds: Flow<List<String>> = dao.observeIds()
    val downloads: Flow<List<LocalMusicDownloadEntity>> = dao.observeAll()

    /**
     * Zwei parallele Slots. Der Server lädt ohnehin seriell; der zweite Slot
     * wartet meist nur mit — aber das Abholen fertiger Dateien überlappt so
     * mit dem nächsten Server-Download.
     */
    private val slots = Semaphore(2)

    /** Server-Statusfelder; org.json ist im Unit-Test-JVM nur ein Stub, kotlinx nicht. */
    private val json = Json { ignoreUnknownKeys = true }

    private val musicDir: File by lazy { File(ctx.filesDir, "music").apply { mkdirs() } }

    suspend fun download(song: MusicSong): Result<LocalMusicDownloadEntity> =
        withContext(Dispatchers.IO) {
            if (isDownloaded(song.videoId)) {
                return@withContext dao.get(song.videoId)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("Download-Eintrag verschwunden"))
            }
            // Platzhalter sofort setzen, damit die UI reagiert, bevor die
            // (langsame) Auflösung überhaupt startet.
            _progress.put(song.videoId, 0f)
            slots.withPermit {
                val target = File(musicDir, "${song.videoId}.m4a")
                try {
                    // Abbruch, der schon im Warteslot ankam
                    if (song.videoId in cancelRequests) throw DownloadCancelled()

                    val backend = repo.backendBase()
                    val url: String
                    val transferFrom: Float
                    if (backend != null) {
                        awaitServerDownload(backend, song.videoId)
                        url = "$backend/music/audio/${song.videoId}"
                        transferFrom = SERVER_PHASE_SHARE
                    } else {
                        // Ohne Backend bleibt nur der direkte Weg (Piped-Fallback).
                        url = repo.getAudioStream(song.videoId)
                            ?: return@withPermit fail(song.videoId, target, "Kein Audio-Stream gefunden")
                        transferFrom = 0f
                    }
                    if (song.videoId in cancelRequests) throw DownloadCancelled()

                    fetchToFile(url, song.videoId, target, transferFrom)?.let { error ->
                        return@withPermit fail(song.videoId, target, error)
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

    /**
     * Reiht den Song auf dem Server ein und wartet, bis die Datei dort liegt.
     *
     * Ein wartender Server (YouTube-Drossel) ist kein Fehler: Die Schleife
     * fragt weiter nach, bis der Server „done" oder „failed" meldet — er hat
     * sein eigenes Zeitbudget (Stunden), wir setzen keines obendrauf.
     */
    private suspend fun awaitServerDownload(backend: String, videoId: String) {
        val endpoint = "$backend/music/download/$videoId"
        client.newCall(Request.Builder().url(endpoint).post(ByteArray(0).toRequestBody()).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw ServerDownloadFailed("Server-Warteschlange: HTTP ${resp.code}")
            }

        var networkErrors = 0
        while (true) {
            if (videoId in cancelRequests) {
                // Wartenden Song auch serverseitig wieder herausnehmen.
                runCatching {
                    client.newCall(Request.Builder().url(endpoint).delete().build()).execute().close()
                }
                throw DownloadCancelled()
            }
            val job: AudioJobDto
            try {
                job = client.newCall(Request.Builder().url(endpoint).get().build()).execute().use { resp ->
                    if (resp.code == 404) throw ServerDownloadFailed("Server kennt den Download nicht mehr")
                    if (!resp.isSuccessful) throw ServerDownloadFailed("Server-Status: HTTP ${resp.code}")
                    json.decodeFromString<AudioJobDto>(resp.body?.string().orEmpty())
                }
                networkErrors = 0
            } catch (e: IOException) {
                // Server kurz weg (WLAN-Wechsel, Neustart): Die Warteschlange
                // dort läuft weiter, wir fragen später einfach noch einmal.
                if (++networkErrors > MAX_NETWORK_ERRORS) {
                    throw ServerDownloadFailed("Server nicht erreichbar: ${e.message}")
                }
                delay(NETWORK_RETRY_MS)
                continue
            }

            when (job.status) {
                "done" -> {
                    _throttleNotice.value = null
                    return
                }
                "failed" -> throw ServerDownloadFailed(
                    job.error?.ifBlank { null } ?: "Download auf dem Server fehlgeschlagen",
                )
                "waiting" -> {
                    _throttleNotice.value =
                        "YouTube drosselt gerade — Downloads laufen automatisch weiter, sobald es wieder geht"
                }
                "downloading" -> {
                    _throttleNotice.value = null
                    val total = job.total ?: 0L
                    if (total > 0) {
                        _progress.put(
                            videoId,
                            (job.bytes.toFloat() / total * SERVER_PHASE_SHARE).coerceIn(0f, SERVER_PHASE_SHARE),
                        )
                    }
                }
            }
            delay(POLL_MS)
        }
    }

    /**
     * Holt [url] nach [target]; Fortschritt läuft von [from] bis 1. Liefert
     * eine Fehlermeldung oder null bei Erfolg.
     */
    private fun fetchToFile(url: String, videoId: String, target: File, from: Float): String? {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return "HTTP ${resp.code}"
            val body = resp.body ?: return "Leere Antwort"
            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        if (videoId in cancelRequests) throw DownloadCancelled()
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        written += n
                        if (totalBytes > 0) {
                            val share = (written.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _progress.put(videoId, from + (1f - from) * share)
                        }
                    }
                }
            }
        }
        return null
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

    /** Antwort von `GET /music/download/:id`. */
    @Serializable
    private data class AudioJobDto(
        val status: String = "",
        val bytes: Long = 0L,
        val total: Long? = null,
        val error: String? = null,
    )

    private companion object {
        /** Anteil des Fortschrittsbalkens für den Server-Download; der Rest ist das Abholen. */
        const val SERVER_PHASE_SHARE = 0.85f
        const val POLL_MS = 1_500L
        const val NETWORK_RETRY_MS = 5_000L
        const val MAX_NETWORK_ERRORS = 24
    }
}
