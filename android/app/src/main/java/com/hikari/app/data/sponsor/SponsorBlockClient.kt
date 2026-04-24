package com.hikari.app.data.sponsor

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit

private val CATEGORIES_JSON = SegmentCategories.all
    .joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.apiKey}\"" }

@Singleton
class SponsorBlockClient @Inject constructor(client: OkHttpClient, json: Json) {
    private val api = Retrofit.Builder()
        .baseUrl("https://sponsor.ajay.pw/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SponsorBlockApi::class.java)

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> =
        runCatching {
            api.skipSegments(videoId, CATEGORIES_JSON).map {
                SponsorSegment(
                    startSeconds = it.segment[0],
                    endSeconds = it.segment[1],
                    category = it.category,
                )
            }
        }.getOrElse { e ->
            if (e is HttpException && e.code() == 404) emptyList()
            else emptyList()
        }
}
