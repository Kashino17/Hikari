package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.PollResponse
import com.hikari.app.data.api.dto.RecommendationDto
import com.hikari.app.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

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
            lastPolledAt = it.last_polled_at,
        )
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

    suspend fun listVideos(channelId: String): List<ChannelVideoDto> =
        api.getChannelVideos(channelId)

    suspend fun deleteVideo(videoId: String) {
        api.deleteVideo(videoId)
    }

    suspend fun importVideos(urls: List<String>): Int =
        api.importVideos(com.hikari.app.data.api.dto.ImportVideosRequest(urls)).queued
}
