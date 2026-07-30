package com.hikari.app.data.api

import com.hikari.app.data.api.dto.PipedSearchResponse
import com.hikari.app.data.api.dto.PipedStreamResponse
import com.hikari.app.data.api.dto.PipedSuggestionResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApi {
    companion object {
        const val BASE_URL = "https://pipedapi.kavin.rocks/"
    }

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("filter") filter: String = "music_songs",
    ): PipedSearchResponse

    @GET("streams/music")
    suspend fun getStreams(
        @Query("videoId") videoId: String,
    ): PipedStreamResponse

    @GET("suggestions")
    suspend fun getSuggestions(
        @Query("q") query: String,
    ): PipedSuggestionResponse
}
