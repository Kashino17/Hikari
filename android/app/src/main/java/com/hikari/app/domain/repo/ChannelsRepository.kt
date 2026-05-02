package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.ForceWindowResponseDto
import com.hikari.app.data.api.dto.PollResponse
import com.hikari.app.data.api.dto.RecommendationDto
import com.hikari.app.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class ChannelsRepository @Inject constructor(
    private val api: HikariApi,
) {
    suspend fun list(): List<Channel> = api.getChannels().map {
        Channel(
            id = it.id,
            url = it.url,
            title = it.title,
            handle = it.handle,
            description = it.description,
            subscribers = it.subscribers,
            thumbnail = it.thumbnail_url,
            bannerUrl = it.banner_url,
            lastPolledAt = it.last_polled_at,
            autoApprove = it.autoApprove == 1,
        )
    }

    suspend fun setAutoApprove(channelId: String, autoApprove: Boolean): Boolean {
        val res = api.setChannelAutoApprove(
            channelId,
            com.hikari.app.data.api.dto.SetAutoApproveRequest(autoApprove),
        )
        return res.autoApprove
    }

    suspend fun listWithStats(): List<Pair<Channel, ChannelStatsDto?>> {
        val channels = list()
        return channels.map { ch ->
            val stats = runCatching { api.getChannelStats(ch.id) }.getOrNull()
            ch to stats
        }
    }

    suspend fun search(query: String): List<ChannelSearchResultDto> =
        api.searchChannels(query)

    suspend fun recommendations(force: Boolean = false): List<RecommendationDto> =
        api.getRecommendations(force = if (force) "true" else null)

    suspend fun add(url: String): Channel {
        val res = api.addChannel(AddChannelRequest(channelUrl = url))
        return Channel(id = res.id, url = res.url, title = res.title)
    }

    suspend fun remove(channelId: String) {
        api.deleteChannel(channelId)
    }

    suspend fun poll(channelId: String): PollResponse = api.pollChannel(channelId)

    suspend fun deepScan(channelId: String, limit: Int = 100): PollResponse =
        api.pollChannel(channelId, deep = true, limit = limit)

    suspend fun pollChannel(channelId: String): PollResponse = api.pollChannel(channelId)

    suspend fun forceClipperWindow(): ForceWindowResponseDto = api.forceClipperWindow()

    suspend fun listVideos(channelId: String): List<ChannelVideoDto> =
        api.getChannelVideos(channelId)

    suspend fun deleteVideo(videoId: String) {
        api.deleteVideo(videoId)
    }

    suspend fun analyzeVideo(url: String) = api.analyzeVideo(
        com.hikari.app.data.api.dto.AnalyzeRequest(url),
    )

    suspend fun importVideosBulk(items: List<com.hikari.app.data.api.dto.BulkImportItem>): Int =
        api.importVideosBulk(com.hikari.app.data.api.dto.BulkImportRequest(items)).queued

    suspend fun listSeries(): List<com.hikari.app.data.api.dto.SeriesItemDto> =
        api.listSeries()

    suspend fun listLanguages(): com.hikari.app.data.api.dto.LanguagesResponse =
        api.listLanguages()

    suspend fun getVideo(id: String): com.hikari.app.data.api.dto.VideoDetailDto =
        api.getVideo(id)

    suspend fun updateVideo(
        id: String,
        req: com.hikari.app.data.api.dto.UpdateVideoRequest,
    ): com.hikari.app.data.api.dto.VideoDetailDto = api.updateVideo(id, req)

    suspend fun uploadVideoThumbnail(
        id: String,
        bytes: ByteArray,
        mime: String,
    ): com.hikari.app.data.api.dto.VideoDetailDto {
        val body = bytes.toRequestBody(mime.toMediaType())
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val part = MultipartBody.Part.createFormData("cover", "thumb.$ext", body)
        return api.uploadVideoThumbnail(id, part)
    }
}
