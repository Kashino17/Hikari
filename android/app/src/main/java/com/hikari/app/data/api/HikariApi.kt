package com.hikari.app.data.api

import com.hikari.app.data.api.dto.BudgetBody
import com.hikari.app.data.api.dto.ServerDownloadStatus
import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.AddChannelResponse
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.SetAutoApproveRequest
import com.hikari.app.data.api.dto.SetAutoApproveResponse
import com.hikari.app.data.api.dto.ChannelStatsDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.ClearOverrideRequest
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.api.dto.FilterStateDto
import com.hikari.app.data.api.dto.AnalyzeRequest
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.ArtistDto
import com.hikari.app.data.api.dto.ArtistPlaylistDto
import com.hikari.app.data.api.dto.BulkImportRequest
import com.hikari.app.data.api.dto.BulkImportResponse
import com.hikari.app.data.api.dto.DiscoveryResponseDto
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.api.dto.LanguagesResponse
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.api.dto.MangaArcManifestDto
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
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.data.api.dto.SetOverrideRequest
import com.hikari.app.data.api.dto.UpdateSeriesRequest
import com.hikari.app.data.api.dto.UpdateVideoRequest
import com.hikari.app.data.api.dto.VideoDetailDto
import com.hikari.app.data.api.dto.VideoFullDto
import com.hikari.app.data.api.dto.ClipperStatusDto
import com.hikari.app.data.api.dto.ForceWindowResponseDto
import com.hikari.app.data.api.dto.LlmHealthDto
import com.hikari.app.data.api.dto.RetryFailedResponse
import com.hikari.app.data.api.dto.TodayCountResponse
import com.hikari.app.data.api.dto.UpdateFilterRequest
import com.hikari.app.data.api.dto.WeeklyStatsDto
import com.hikari.app.data.api.dto.ArtistPageDto
import com.hikari.app.data.api.dto.HomeFeedDto
import com.hikari.app.data.api.dto.MusicStreamDto
import com.hikari.app.data.api.dto.MusicTrackDto
import com.hikari.app.data.api.dto.FullSearchDto
import com.hikari.app.data.api.dto.SearchAlbumDto
import com.hikari.app.data.api.dto.SearchArtistDto
import com.hikari.app.data.api.dto.SearchPlaylistDto
import com.hikari.app.data.api.dto.SuggestionDto
import com.hikari.app.data.api.dto.NewsItemDto
import com.hikari.app.data.api.dto.NewsTopicDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface HikariApi {
    @GET("music/search")
    suspend fun searchMusic(
        @Query("q") query: String,
        @Query("mode") mode: String = "music",
    ): List<MusicTrackDto>

    @GET("music/stream/{videoId}")
    suspend fun getMusicStream(
        @Path("videoId") videoId: String,
        @Query("force") force: Boolean? = null,
    ): MusicStreamDto

    @GET("music/artist/{channelId}")
    suspend fun getArtist(@Path("channelId") channelId: String): ArtistDto

    @GET("music/artist/{channelId}/top")
    suspend fun getArtistTop(
        @Path("channelId") channelId: String,
        @Query("name") name: String,
    ): List<MusicTrackDto>

    @GET("music/artist/{channelId}/playlists")
    suspend fun getArtistPlaylists(
        @Path("channelId") channelId: String,
        @Query("name") name: String,
    ): List<ArtistPlaylistDto>

    @GET("music/suggestions")
    suspend fun getSuggestions(@Query("q") q: String): List<SuggestionDto>

    @GET("music/search/full")
    suspend fun searchFullMusic(
        @Query("q") q: String,
        @Query("mode") mode: String = "music",
    ): FullSearchDto

    /** Vier Methoden mit festem type-Query — gleicher Pfad, sauber typisiert. */
    @GET("music/search/typed?type=songs")
    suspend fun searchTypedSongs(@Query("q") q: String): List<MusicTrackDto>

    @GET("music/search/typed?type=albums")
    suspend fun searchTypedAlbums(@Query("q") q: String): List<SearchAlbumDto>

    @GET("music/search/typed?type=artists")
    suspend fun searchTypedArtists(@Query("q") q: String): List<SearchArtistDto>

    @GET("music/search/typed?type=playlists")
    suspend fun searchTypedPlaylists(@Query("q") q: String): List<SearchPlaylistDto>

    /** Tracks einer Remote-Playlist oder eines Albums. */
    @GET("music/playlist/{playlistId}")
    suspend fun getPlaylistTracks(@Path("playlistId") playlistId: String): List<MusicTrackDto>

    /** YouTube-Music-Home-Feed (generisch, Personalisierung passiert im Client). */
    @GET("music/home")
    suspend fun getMusicHome(): HomeFeedDto

    /** Radio-Queue zu einem Song — die Basis für Autoplay und "Dein Mix". */
    @GET("music/related/{videoId}")
    suspend fun getRelatedSongs(@Path("videoId") videoId: String): List<MusicTrackDto>

    /**
     * Komplette Artist-Seite (Profil, Top-Songs, Alben, Singles, Related) in
     * einem Call. [name] hilft dem Backend-Fallback für normale YouTube-Kanäle
     * (True Crime, Podcasts), die YouTube Music nicht als Artist kennt.
     */
    @GET("music/artist/{channelId}/page")
    suspend fun getArtistPage(
        @Path("channelId") channelId: String,
        @Query("name") name: String,
    ): ArtistPageDto

    // ── Täglicher KI-Tagesbericht ─────────────────────────────────────────
    @GET("news/topics")
    suspend fun getNewsTopics(): List<NewsTopicDto>

    @GET("news/briefing")
    suspend fun getNewsBriefing(
        @Query("topics") topics: String,
        @Query("city") city: String? = null,
        @Query("lang") lang: String = "de",
        @Query("force") force: Boolean? = null,
    ): List<NewsItemDto>

    @GET("feed")
    suspend fun getFeed(
        @Query("mode") mode: String = "new",
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: Int? = null,
    ): List<FeedItemDto>

    @POST("feed/{id}/seen")
    suspend fun markSeen(@Path("id") videoId: String)

    @GET("watch-later")
    suspend fun getWatchLater(): List<FeedItemDto>

    @POST("watch-later/{id}")
    suspend fun addWatchLater(@Path("id") videoId: String)

    @DELETE("watch-later/{id}")
    suspend fun removeWatchLater(@Path("id") videoId: String)

    @POST("videos/{id}/download")
    suspend fun requestServerDownload(@Path("id") videoId: String): ServerDownloadStatus

    @GET("feed/budget")
    suspend fun getBudget(): BudgetBody

    @PUT("feed/budget")
    suspend fun setBudget(@Body body: BudgetBody): BudgetBody

    @POST("channels/{id}/subscribe")
    suspend fun subscribeChannel(@Path("id") channelId: String)

    @POST("channels/{id}/block")
    suspend fun blockChannel(@Path("id") channelId: String)

    @POST("feed/{id}/save")
    suspend fun save(@Path("id") videoId: String)

    @PUT("feed/{id}/progress")
    suspend fun setProgress(
        @Path("id") videoId: String,
        @Body body: com.hikari.app.data.api.dto.ProgressBody,
    )

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

    @GET("queue")
    suspend fun getQueue(): List<FeedItemDto>

    @POST("queue/{id}")
    suspend fun addToQueue(@Path("id") videoId: String)

    @DELETE("queue/{id}")
    suspend fun removeFromQueue(@Path("id") videoId: String)

    @GET("channels")
    suspend fun getChannels(): List<ChannelDto>

    @GET("channels/search")
    suspend fun searchChannels(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): List<ChannelSearchResultDto>

    @GET("channels/recommendations")
    suspend fun getRecommendations(@Query("force") force: String? = null): List<RecommendationDto>

    @GET("discovery")
    suspend fun getDiscovery(
        @Query("limit") limit: Int = 12,
        @Query("longFormMinSeconds") longFormMinSeconds: Int? = null,
    ): DiscoveryResponseDto

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

    @PATCH("channels/{id}/auto-approve")
    suspend fun setChannelAutoApprove(
        @Path("id") channelId: String,
        @Body req: SetAutoApproveRequest,
    ): SetAutoApproveResponse

    @GET("channels/{id}/stats")
    suspend fun getChannelStats(@Path("id") channelId: String): ChannelStatsDto

    @GET("channels/{id}/videos")
    suspend fun getChannelVideos(@Path("id") channelId: String): List<ChannelVideoDto>

    @GET("rejected")
    suspend fun getRejected(@Query("limit") limit: Int = 50): List<RejectedItemDto>

    @GET("library")
    suspend fun getLibrary(): LibraryResponse

    @GET("downloads")
    suspend fun getDownloads(): DownloadsResponse

    @GET("series/{id}")
    suspend fun getSeries(@Path("id") seriesId: String): SeriesDetailResponse

    @PATCH("series/{id}")
    suspend fun updateSeries(@Path("id") seriesId: String, @Body req: UpdateSeriesRequest): SeriesDto

    @Multipart
    @POST("series/{id}/cover")
    suspend fun uploadSeriesCover(
        @Path("id") seriesId: String,
        @Part cover: MultipartBody.Part,
    ): SeriesDto

    @GET("stats/weekly")
    suspend fun getWeeklyStats(): WeeklyStatsDto

    @POST("videos/analyze")
    suspend fun analyzeVideo(@Body req: AnalyzeRequest): AnalyzeResponse

    @POST("videos/import/bulk")
    suspend fun importVideosBulk(@Body req: BulkImportRequest): BulkImportResponse

    @GET("videos/{id}")
    suspend fun getVideo(@Path("id") videoId: String): VideoDetailDto

    @PATCH("videos/{id}")
    suspend fun updateVideo(
        @Path("id") videoId: String,
        @Body req: UpdateVideoRequest,
    ): VideoDetailDto

    @Multipart
    @POST("videos/{id}/thumbnail")
    suspend fun uploadVideoThumbnail(
        @Path("id") videoId: String,
        @Part cover: MultipartBody.Part,
    ): VideoDetailDto

    @GET("series")
    suspend fun listSeries(): List<SeriesItemDto>

    @GET("languages")
    suspend fun listLanguages(): LanguagesResponse

    @GET("filter")
    suspend fun getFilter(): FilterStateDto

    @PUT("filter")
    suspend fun updateFilter(@Body req: UpdateFilterRequest): FilterStateDto

    @PUT("filter")
    suspend fun setPromptOverride(@Body req: SetOverrideRequest): FilterStateDto

    @PUT("filter")
    suspend fun clearPromptOverride(@Body req: ClearOverrideRequest = ClearOverrideRequest()): FilterStateDto

    // ── Per-channel filter ────────────────────────────────────────────────
    @GET("channels/{id}/filter")
    suspend fun getChannelFilter(@Path("id") channelId: String): FilterStateDto

    @PUT("channels/{id}/filter")
    suspend fun updateChannelFilter(
        @Path("id") channelId: String,
        @Body req: UpdateFilterRequest,
    ): FilterStateDto

    @PUT("channels/{id}/filter")
    suspend fun setChannelPromptOverride(
        @Path("id") channelId: String,
        @Body req: SetOverrideRequest,
    ): FilterStateDto

    @DELETE("channels/{id}/filter")
    suspend fun clearChannelFilter(@Path("id") channelId: String)

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

    @GET("api/manga/arcs/{arcId}/manifest")
    suspend fun getMangaArcManifest(@Path("arcId") arcId: String): MangaArcManifestDto

    @POST("api/manga/arcs/{arcId}/download")
    suspend fun startMangaArcDownload(@Path("arcId") arcId: String)

    @GET("clipper/status")
    suspend fun getClipperStatus(): ClipperStatusDto

    @POST("clipper/retry-failed")
    suspend fun retryFailed(): RetryFailedResponse

    @POST("clipper/force-window")
    suspend fun forceClipperWindow(): ForceWindowResponseDto

    @GET("clipper/llm-health")
    suspend fun getLlmHealth(): LlmHealthDto

    @GET("videos/{id}/full")
    suspend fun getVideoFull(@Path("id") id: String): VideoFullDto
}
