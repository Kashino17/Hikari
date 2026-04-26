package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.AddChannelResponse
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.ClearOverrideRequest
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.api.dto.FilterStateDto
import com.hikari.app.data.api.dto.AnalyzeRequest
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportRequest
import com.hikari.app.data.api.dto.BulkImportResponse
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.data.api.dto.MangaProgressRequest
import com.hikari.app.data.api.dto.MangaSeriesDetailDto
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.data.api.dto.PollResponse
import com.hikari.app.data.api.dto.RecommendationDto
import com.hikari.app.data.api.dto.RejectedItemDto
import com.hikari.app.data.api.dto.SeriesDetailResponse
import com.hikari.app.data.api.dto.SetOverrideRequest
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.api.dto.UpdateFilterRequest
import com.hikari.app.data.api.dto.WeeklyStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @DELETE("feed/{id}")
    suspend fun deleteVideo(@Path("id") videoId: String)

    @GET("feed/today-count")
    suspend fun todayCount(): TodayCountResponse

    @GET("channels")
    suspend fun getChannels(): List<ChannelDto>

    @GET("channels/search")
    suspend fun searchChannels(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): List<ChannelSearchResultDto>

    @GET("channels/recommendations")
    suspend fun getRecommendations(@Query("force") force: String? = null): List<RecommendationDto>

    @POST("channels")
    suspend fun addChannel(@Body req: AddChannelRequest): AddChannelResponse

    @DELETE("channels/{id}")
    suspend fun deleteChannel(@Path("id") channelId: String)

    @POST("channels/{id}/poll")
    suspend fun pollChannel(
        @Path("id") channelId: String,
        @Query("deep") deep: Boolean? = null,
        @Query("limit") limit: Int? = null,
    ): PollResponse

    @GET("channels/{id}/stats")
    suspend fun getChannelStats(@Path("id") channelId: String): ChannelStatsDto

    @GET("channels/{id}/videos")
    suspend fun getChannelVideos(@Path("id") channelId: String): List<ChannelVideoDto>

    @GET("rejected")
    suspend fun getRejected(@Query("limit") limit: Int = 50): List<RejectedItemDto>

    @GET("library")
    suspend fun getLibrary(): LibraryResponse

    @GET("series/{id}")
    suspend fun getSeries(@Path("id") seriesId: String): SeriesDetailResponse

    @GET("stats/weekly")
    suspend fun getWeeklyStats(): WeeklyStatsDto

    @POST("videos/analyze")
    suspend fun analyzeVideo(@Body req: AnalyzeRequest): AnalyzeResponse

    @POST("videos/import/bulk")
    suspend fun importVideosBulk(@Body req: BulkImportRequest): BulkImportResponse

    @GET("series")
    suspend fun listSeries(): List<SeriesItemDto>

    @GET("filter")
    suspend fun getFilter(): FilterStateDto

    @PUT("filter")
    suspend fun updateFilter(@Body req: UpdateFilterRequest): FilterStateDto

    @PUT("filter")
    suspend fun setPromptOverride(@Body req: SetOverrideRequest): FilterStateDto

    @PUT("filter")
    suspend fun clearPromptOverride(@Body req: ClearOverrideRequest = ClearOverrideRequest()): FilterStateDto

    @GET("api/manga/series")
    suspend fun listMangaSeries(): List<MangaSeriesDto>

    @GET("api/manga/series/{id}")
    suspend fun getMangaSeries(@Path("id") id: String): MangaSeriesDetailDto

    @GET("api/manga/chapters/{id}/pages")
    suspend fun getMangaChapterPages(@Path("id") id: String): List<MangaPageDto>

    @GET("api/manga/continue")
    suspend fun getMangaContinue(): List<MangaContinueDto>

    @POST("api/manga/library/{id}")
    suspend fun addMangaToLibrary(@Path("id") seriesId: String)

    @DELETE("api/manga/library/{id}")
    suspend fun removeMangaFromLibrary(@Path("id") seriesId: String)

    @PUT("api/manga/progress/{seriesId}")
    suspend fun setMangaProgress(
        @Path("seriesId") seriesId: String,
        @Body body: MangaProgressRequest,
    )

    @PUT("api/manga/chapters/{id}/read")
    suspend fun markMangaChapterRead(@Path("id") chapterId: String)

    @POST("api/manga/chapters/{id}/sync")
    suspend fun startMangaChapterSync(@Path("id") chapterId: String)

    @POST("api/manga/sync")
    suspend fun startMangaSync()

    @GET("api/manga/sync/jobs")
    suspend fun listMangaSyncJobs(): List<MangaSyncJobDto>
}
