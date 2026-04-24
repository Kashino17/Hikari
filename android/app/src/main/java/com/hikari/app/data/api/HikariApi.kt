package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.AddChannelResponse
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.api.dto.PollResponse
import com.hikari.app.data.api.dto.RejectedItemDto
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.api.dto.WeeklyStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HikariApi {
    @GET("feed")
    suspend fun getFeed(@Query("mode") mode: String = "new"): List<FeedItemDto>

    @POST("feed/{id}/seen")
    suspend fun markSeen(@Path("id") videoId: String)

    @POST("feed/{id}/save")
    suspend fun save(@Path("id") videoId: String)

    @DELETE("feed/{id}/save")
    suspend fun unsave(@Path("id") videoId: String)

    @POST("feed/{id}/unplayable")
    suspend fun markUnplayable(@Path("id") videoId: String)

    @POST("feed/{id}/less-like-this")
    suspend fun lessLikeThis(@Path("id") videoId: String)

    @GET("feed/today-count")
    suspend fun todayCount(): TodayCountResponse

    @GET("channels")
    suspend fun getChannels(): List<ChannelDto>

    @POST("channels")
    suspend fun addChannel(@Body req: AddChannelRequest): AddChannelResponse

    @DELETE("channels/{id}")
    suspend fun deleteChannel(@Path("id") channelId: String)

    @POST("channels/{id}/poll")
    suspend fun pollChannel(@Path("id") channelId: String): PollResponse

    @GET("channels/{id}/stats")
    suspend fun getChannelStats(@Path("id") channelId: String): ChannelStatsDto

    @GET("rejected")
    suspend fun getRejected(@Query("limit") limit: Int = 50): List<RejectedItemDto>

    @GET("stats/weekly")
    suspend fun getWeeklyStats(): WeeklyStatsDto
}
