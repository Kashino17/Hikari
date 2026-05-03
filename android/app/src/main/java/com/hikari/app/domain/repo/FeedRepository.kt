package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.CaptionDto
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.LibraryVideoDto
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.api.dto.UpdateSeriesRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.FeedItemEntity
import com.hikari.app.domain.model.Caption
import com.hikari.app.domain.model.FeedItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FeedRepository @Inject constructor(
    private val api: HikariApi,
    private val dao: FeedDao,
) {
    fun newItems(): Flow<List<FeedItem>> =
        dao.newItems().map { rows -> rows.map { it.toDomain() } }

    suspend fun refresh() {
        val remote = api.getFeed(mode = "new")
        dao.upsertAll(remote.map { it.toEntity() })
        if (remote.isEmpty()) {
            dao.pruneAll()
        } else {
            dao.pruneNotIn(remote.map { it.videoId })
        }
    }

    suspend fun fetchSaved(): List<FeedItem> =
        api.getFeed(mode = "saved").map { it.toDomain() }

    /** Fetch history (seen videos) directly from API — no Room caching for browse history. */
    suspend fun fetchOld(): List<FeedItem> =
        api.getFeed(mode = "old").map { it.toDomain() }

    suspend fun fetchQueue(): List<FeedItem> =
        api.getQueue().map { it.toDomain() }

    suspend fun addToQueue(videoId: String) {
        api.addToQueue(videoId)
    }

    suspend fun removeFromQueue(videoId: String) {
        api.removeFromQueue(videoId)
    }

    suspend fun markSeen(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markSeen(videoId) }
    }

    suspend fun toggleSave(videoId: String, currentlySaved: Boolean): Boolean {
        val new = !currentlySaved
        dao.setSaved(videoId, new)
        try {
            if (new) api.save(videoId) else api.unsave(videoId)
            return new
        } catch (error: Exception) {
            dao.setSaved(videoId, currentlySaved)
            throw error
        }
    }

    suspend fun markUnplayable(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markUnplayable(videoId) }
    }

    suspend fun lessLikeThis(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.lessLikeThis(videoId) }
    }

    suspend fun delete(videoId: String) {
        dao.delete(videoId)
        runCatching { api.deleteVideo(videoId) }
    }

    suspend fun todayCount(): TodayCountResponse = api.todayCount()

    suspend fun getLibrary(): LibraryResponse = api.getLibrary()

    suspend fun getDownloads(): com.hikari.app.data.api.dto.DownloadsResponse =
        api.getDownloads()

    suspend fun getSeries(id: String): SeriesDetailResponse = api.getSeries(id)

    suspend fun updateSeries(id: String, thumbnailUrl: String?, description: String?): SeriesDto =
        api.updateSeries(id, UpdateSeriesRequest(thumbnail_url = thumbnailUrl, description = description))

    suspend fun uploadSeriesCover(id: String, bytes: ByteArray, mime: String): SeriesDto {
        val body = bytes.toRequestBody(mime.toMediaType())
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val part = MultipartBody.Part.createFormData("cover", "cover.$ext", body)
        return api.uploadSeriesCover(id, part)
    }
}

private fun FeedItemDto.toEntity() = FeedItemEntity(
    videoId = videoId, kind = kind, parentVideoId = parentVideoId,
    title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelId = channelId, channelTitle = channelTitle,
    category = category, reasoning = reasoning,
    overallScore = overallScore, educationalValue = educationalValue,
    addedAt = addedAt, saved = saved == 1, seen = seenAt != null,
    captionsJson = captions?.let { runCatching { Json.encodeToString(it) }.getOrNull() },
    context = context,
)

private fun FeedItemEntity.toDomain() = FeedItem(
    videoId = videoId, kind = kind, parentVideoId = parentVideoId,
    title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle, category = category,
    reasoning = reasoning, saved = saved,
    overallScore = overallScore, educationalValue = educationalValue,
    captions = captionsJson?.let {
        runCatching {
            Json.decodeFromString<List<CaptionDto>>(it).map { c ->
                Caption(
                    startMs = (c.start * 1000).toLong(),
                    endMs = (c.end * 1000).toLong(),
                    text = c.text,
                )
            }
        }.getOrNull()
    },
    context = context,
)

private fun FeedItemDto.toDomain() = FeedItem(
    videoId = videoId, kind = kind, parentVideoId = parentVideoId,
    title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle, category = category,
    reasoning = reasoning, saved = saved == 1,
    overallScore = overallScore, educationalValue = educationalValue,
    captions = captions?.map { c ->
        Caption(
            startMs = (c.start * 1000).toLong(),
            endMs = (c.end * 1000).toLong(),
            text = c.text,
        )
    },
    context = context,
)
