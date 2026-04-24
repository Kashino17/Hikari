package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.FeedItemEntity
import com.hikari.app.domain.model.FeedItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FeedRepository @Inject constructor(
    private val api: HikariApi,
    private val dao: FeedDao,
) {
    fun unseenItems(): Flow<List<FeedItem>> =
        dao.unseenItems().map { rows -> rows.map { it.toDomain() } }

    fun savedItems(): Flow<List<FeedItem>> =
        dao.savedItems().map { rows -> rows.map { it.toDomain() } }

    suspend fun refresh() {
        val remote = api.getFeed(mode = "new")
        dao.upsertAll(remote.map { it.toEntity() })
        dao.pruneNotIn(remote.map { it.videoId })
    }

    /** Fetch history (seen videos) directly from API — no Room caching for browse history. */
    suspend fun fetchOld(): List<FeedItem> =
        api.getFeed(mode = "old").map { it.toDomainOld() }

    suspend fun markSeen(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markSeen(videoId) }
    }

    suspend fun toggleSave(videoId: String, currentlySaved: Boolean) {
        val new = !currentlySaved
        dao.setSaved(videoId, new)
        runCatching { if (new) api.save(videoId) else api.unsave(videoId) }
    }

    suspend fun markUnplayable(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markUnplayable(videoId) }
    }

    suspend fun lessLikeThis(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.lessLikeThis(videoId) }
    }

    suspend fun todayCount(): TodayCountResponse = api.todayCount()
}

private fun FeedItemDto.toEntity() = FeedItemEntity(
    videoId = videoId, title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelId = channelId, channelTitle = channelTitle,
    category = category, reasoning = reasoning,
    addedAt = addedAt, saved = saved == 1, seen = false,
)

private fun FeedItemEntity.toDomain() = FeedItem(
    videoId = videoId, title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle, category = category,
    reasoning = reasoning, saved = saved,
)

// For mode=old: maps a DTO directly to domain (API order = seen_at DESC, no Room sort needed).
private fun FeedItemDto.toDomainOld() = FeedItem(
    videoId = videoId, title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle, category = category,
    reasoning = reasoning, saved = saved == 1,
)
