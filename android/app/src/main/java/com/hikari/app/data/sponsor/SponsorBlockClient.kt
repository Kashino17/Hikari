package com.hikari.app.data.sponsor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class SkipSegmentDto(
    val category: String = "",
    val actionType: String = "skip",
    val segment: List<Double> = emptyList(),
)

/** Echte YouTube-Video-IDs: genau 11 Zeichen. Importierte Folgen (sniff_…, voe_…) haben keine Segmente. */
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

private val CATEGORIES_JSON = SegmentCategories.all
    .joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.apiKey}\"" }

/**
 * Holt die von der Community markierten Abschnitte eines YouTube-Videos
 * (Sponsor, Intro, Outro …) direkt bei SponsorBlock.
 *
 * Nutzt absichtlich einen eigenen, schlichten OkHttpClient: der App-weite
 * Client aus dem NetworkModule schreibt jeden Request per Interceptor auf die
 * Backend-URL um — die Anfrage käme nie bei SponsorBlock an.
 *
 * Antworten landen zusätzlich als kleine JSON-Datei im Cache, damit auch
 * heruntergeladene Videos ohne Netz ihre Abschnitte kennen.
 */
@Singleton
class SponsorBlockClient @Inject constructor(
    @ApplicationContext ctx: Context,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cacheDir = File(ctx.cacheDir, "sponsorblock")
    private val memory = ConcurrentHashMap<String, Pair<Long, List<SponsorSegment>>>()

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (!YOUTUBE_ID.matches(videoId)) return@withContext emptyList()
        memory[videoId]?.let { (at, cached) ->
            if (System.currentTimeMillis() - at < MEMORY_TTL_MS) return@withContext cached
        }
        val fresh = fetchRemote(videoId)
        if (fresh != null) {
            memory[videoId] = System.currentTimeMillis() to fresh
            return@withContext fresh
        }
        // Kein Netz oder Serverfehler: letzter bekannter Stand, sonst nichts.
        readDisk(videoId).orEmpty()
    }

    /** Liste bei Erfolg (auch leer bei bestätigtem „keine Abschnitte"), null bei Fehlschlag. */
    private fun fetchRemote(videoId: String): List<SponsorSegment>? {
        val url = "$BASE_URL/api/skipSegments".toHttpUrl().newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("categories", CATEGORIES_JSON)
            .build()
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    // 404 heißt bei SponsorBlock: für dieses Video ist nichts markiert.
                    response.code == 404 -> emptyList<SponsorSegment>().also { writeDisk(videoId, "[]") }
                    !response.isSuccessful -> null
                    else -> {
                        val body = response.body.string()
                        parse(body).also { writeDisk(videoId, body) }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(body: String): List<SponsorSegment> =
        json.decodeFromString<List<SkipSegmentDto>>(body)
            .filter { it.actionType == "skip" && it.segment.size >= 2 && it.segment[1] > it.segment[0] }
            .map { SponsorSegment(startSeconds = it.segment[0], endSeconds = it.segment[1], category = it.category) }
            .sortedBy { it.startSeconds }

    private fun readDisk(videoId: String): List<SponsorSegment>? = runCatching {
        File(cacheDir, "$videoId.json").takeIf { it.exists() }?.readText()?.let(::parse)
    }.getOrNull()

    private fun writeDisk(videoId: String, body: String) {
        runCatching {
            cacheDir.mkdirs()
            File(cacheDir, "$videoId.json").writeText(body)
        }
    }

    private companion object {
        const val BASE_URL = "https://sponsor.ajay.app"
        const val MEMORY_TTL_MS = 30 * 60 * 1000L
    }
}
