package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelsRepository @Inject constructor(
    private val api: HikariApi,
) {
    suspend fun list(): List<Channel> = api.getChannels().map {
        Channel(id = it.id, url = it.url, title = it.title)
    }

    suspend fun add(url: String): Channel {
        val res = api.addChannel(AddChannelRequest(channelUrl = url))
        return Channel(id = res.id, url = res.url, title = res.title)
    }

    suspend fun remove(channelId: String) {
        api.deleteChannel(channelId)
    }
}
