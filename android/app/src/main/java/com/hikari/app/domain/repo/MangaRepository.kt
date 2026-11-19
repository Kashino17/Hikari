package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.MangaProgressRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepository @Inject constructor(
    private val api: HikariApi,
) {
    suspend fun listSeries() = api.listMangaSeries()
    suspend fun catalogHome(lang: String) = api.getMangaCatalogHome(lang)
    suspend fun catalogList(f: MangaCatalogFilter, offset: Int, limit: Int = 30) = api.listMangaCatalog(
        lang = f.lang, genre = f.genre, kind = f.kind, status = f.status, sort = f.sort,
        q = f.q?.takeIf { it.isNotBlank() }, offset = offset, limit = limit,
    )
    /** Reihe aus dem Katalog öffnen: der Mac holt die Kapitelliste; danach gilt sie als normale Reihe. */
    suspend fun openCatalog(id: String) = api.openMangaCatalog(id).seriesId
    /** URL des Katalog-Titelbilds (Pfad kommt vom Mac). */
    fun catalogCoverUrl(baseUrl: String, path: String?): String? = path?.let { baseUrl.trimEnd('/') + it }
    suspend fun getSeries(id: String) = api.getMangaSeries(id)
    suspend fun getChapterPages(chapterId: String) = api.getMangaChapterPages(chapterId)
    suspend fun getContinue() = api.getMangaContinue()

    fun pageImageUrl(baseUrl: String, pageId: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/api/manga/page/$pageId"
    }

    fun coverImageUrl(baseUrl: String, coverPath: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/api/manga/cover/$coverPath"
    }

    suspend fun addToLibrary(seriesId: String) = api.addMangaToLibrary(seriesId)
    suspend fun removeFromLibrary(seriesId: String) = api.removeMangaFromLibrary(seriesId)
    suspend fun setProgress(seriesId: String, chapterId: String, pageNumber: Int, at: Long? = null) =
        api.setMangaProgress(seriesId, MangaProgressRequest(chapterId, pageNumber, at))
    suspend fun markChapterRead(chapterId: String) = api.markMangaChapterRead(chapterId)
    suspend fun startChapterSync(chapterId: String) = api.startMangaChapterSync(chapterId)
    suspend fun startSync() = api.startMangaSync()
    suspend fun listSyncJobs() = api.listMangaSyncJobs()
}

/** Filter für den Manga-Katalog. lang: all | de | en. */
data class MangaCatalogFilter(
    val lang: String = "all",
    val genre: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val sort: String = "pop",
    val q: String? = null,
    val title: String? = null,
)
