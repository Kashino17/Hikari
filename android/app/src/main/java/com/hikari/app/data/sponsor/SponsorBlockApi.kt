package com.hikari.app.data.sponsor

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class SkipSegmentDto(val category: String, val segment: List<Double>)

interface SponsorBlockApi {
    @GET("api/skipSegments")
    suspend fun skipSegments(
        @Query("videoID") videoId: String,
        @Query("categories") categoriesJson: String,
    ): List<SkipSegmentDto>
}
