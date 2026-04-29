package com.hikari.app.domain.download

import android.content.Context
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaArcManifestDto
import com.hikari.app.data.api.dto.MangaArcManifestPageDto
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.data.db.LocalMangaDao
import com.hikari.app.data.db.LocalMangaPageEntity
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lädt einen kompletten Manga-Arc auf das Phone für Offline-Lesen. Drei
 * Phasen pro download():
 *   1. POST /api/manga/arcs/{arcId}/download → Backend syncs Pages auf Disk
 *   2. Poll GET /manifest bis readyPages == total (Backend ist async)
 *   3. Iteriere pages, GET /api/manga/page/{pageId}, ablegen unter
 *      filesDir/manga/<seriesSlug>/<arcOrder>/<chapterNumber>/<pageNumber>.<ext>
 *
 * Idempotent: Pages mit existierender File werden geskippt → Resume nach
 * Crash/App-Kill funktioniert ohne Doppel-Download.
 */
@Singleton
class LocalMangaDownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: LocalMangaDao,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
    private val api: HikariApi,
) {
    /** Per-arcId progress 0f..1f (page-count-basiert). Aus Map entfernt wenn fertig. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    val downloadedArcIds: Flow<List<String>> = dao.observeArcIds()

    private val mangaDir: File by lazy {
        File(ctx.filesDir, "manga").apply { mkdirs() }
    }

    suspend fun isDownloaded(arcId: String): Boolean = dao.getArc(arcId) != null

    suspend fun localPageFile(pageId: String): File? {
        val page = dao.getPage(pageId) ?: return null
        val file = File(page.localFilePath)
        return if (file.exists()) file else null
    }

    suspend fun delete(arcId: String) = withContext(Dispatchers.IO) {
        val arc = dao.getArc(arcId) ?: return@withContext
        // Löscht ganzen Arc-Ordner — sicherer als per-page-File-Delete, falls
        // sich Pfad-Konventionen mal ändern und alte Files herumliegen bleiben.
        File(mangaDir, "${arc.seriesSlug}/${arc.arcOrder}").deleteRecursively()
        dao.deleteArcWithPages(arcId)
    }

    suspend fun download(arcId: String): Result<LocalMangaArcEntity> =
        withContext(Dispatchers.IO) {
            try {
                _progress.update(arcId, 0f)

                // Backend-Sync triggern. Wenn der Arc schon komplett synced
                // ist gibt's praktisch keinen Effekt — Endpoint ist 202 + idempotent.
                runCatching { api.startMangaArcDownload(arcId) }

                val manifest = waitForBackendSync(arcId) ?: run {
                    _progress.remove(arcId)
                    return@withContext Result.failure<LocalMangaArcEntity>(
                        IllegalStateException("backend sync timed out for arc $arcId"),
                    )
                }

                val backend = settings.backendUrl.first().trimEnd('/')
                val arcDir = File(mangaDir, "${manifest.seriesSlug}/${manifest.arcOrder}")
                    .apply { mkdirs() }

                var totalBytes = 0L
                manifest.pages.forEachIndexed { idx, page ->
                    val existing = dao.getPage(page.pageId)
                    val cachedFile = existing?.let { File(it.localFilePath) }
                    if (existing != null && cachedFile?.exists() == true) {
                        totalBytes += existing.byteSize
                    } else {
                        val (file, bytes) = downloadPage(backend, page, arcDir)
                        dao.upsertPage(
                            LocalMangaPageEntity(
                                pageId = page.pageId,
                                arcId = arcId,
                                chapterId = page.chapterId,
                                chapterNumber = page.chapterNumber,
                                pageNumber = page.pageNumber,
                                localFilePath = file.absolutePath,
                                byteSize = bytes,
                                downloadedAt = System.currentTimeMillis(),
                            ),
                        )
                        totalBytes += bytes
                    }
                    _progress.update(arcId, (idx + 1).toFloat() / manifest.pages.size)
                }

                val arc = LocalMangaArcEntity(
                    arcId = manifest.arcId,
                    seriesId = manifest.seriesId,
                    seriesSlug = manifest.seriesSlug,
                    seriesTitle = manifest.seriesTitle,
                    seriesCoverPath = null,
                    arcOrder = manifest.arcOrder,
                    arcTitle = manifest.arcTitle,
                    expectedPageCount = manifest.pages.size,
                    totalByteSize = totalBytes,
                    downloadedAt = System.currentTimeMillis(),
                )
                dao.upsertArc(arc)
                _progress.remove(arcId)
                Result.success(arc)
            } catch (e: Exception) {
                _progress.remove(arcId)
                Result.failure(e)
            }
        }

    /**
     * Pollt /manifest bis Backend alle Pages auf Disk hat. 2s-Intervall, 10min
     * Timeout — bei sehr großen Arcs (300+ Pages) reicht das normalerweise,
     * Timeout-Failure ist ein harter Stop ohne Page-Download-Versuch.
     */
    private suspend fun waitForBackendSync(arcId: String): MangaArcManifestDto? {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var manifest = api.getMangaArcManifest(arcId)
        while (manifest.readyPages < manifest.pages.size) {
            if (System.currentTimeMillis() > deadline) return null
            delay(POLL_INTERVAL_MS)
            manifest = api.getMangaArcManifest(arcId)
        }
        return manifest
    }

    private fun downloadPage(
        backend: String,
        page: MangaArcManifestPageDto,
        arcDir: File,
    ): Pair<File, Long> {
        val req = Request.Builder()
            .url("$backend/api/manga/page/${page.pageId}")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} fetching page ${page.pageId}")
            }
            val ext = extensionFromContentType(resp.header("Content-Type"))
            val chapterDir = File(arcDir, chapterPathSegment(page.chapterNumber))
                .apply { mkdirs() }
            val target = File(
                chapterDir,
                "${page.pageNumber.toString().padStart(3, '0')}$ext",
            )
            val body = resp.body ?: throw IllegalStateException("empty body for ${page.pageId}")
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target to target.length()
        }
    }

    private fun extensionFromContentType(ct: String?): String = when {
        ct == null -> ".jpg"
        ct.contains("png", ignoreCase = true) -> ".png"
        ct.contains("webp", ignoreCase = true) -> ".webp"
        else -> ".jpg"
    }

    /** "12.0" → "12" (Standard-Kapitel), "12.5" → "12.5" (Split-Kapitel). */
    private fun chapterPathSegment(number: Double): String =
        if (number == number.toInt().toDouble()) number.toInt().toString()
        else number.toString()

    private fun MutableStateFlow<Map<String, Float>>.update(id: String, value: Float) {
        this.value = this.value + (id to value)
    }
    private fun MutableStateFlow<Map<String, Float>>.remove(id: String) {
        this.value = this.value - id
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val POLL_TIMEOUT_MS = 10 * 60_000L
    }
}
