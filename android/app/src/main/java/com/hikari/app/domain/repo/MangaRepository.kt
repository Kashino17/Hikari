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
    suspend fun setProgress(seriesId: String, chapterId: String, pageNumber: Int) =
        api.setMangaProgress(seriesId, MangaProgressRequest(chapterId, pageNumber))
    suspend fun markChapterRead(chapterId: String) = api.markMangaChapterRead(chapterId)
    suspend fun startChapterSync(chapterId: String) = api.startMangaChapterSync(chapterId)
    suspend fun startSync() = api.startMangaSync()
    suspend fun listSyncJobs() = api.listMangaSyncJobs()
}
