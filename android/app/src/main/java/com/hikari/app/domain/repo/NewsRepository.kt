package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.NewsItemDto
import com.hikari.app.data.api.dto.NewsTopicDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.NewsItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Lädt den täglichen KI-Tagesbericht vom Backend. Die Auswahl (Themen, Stadt,
 * Sprache) kommt aus dem [SettingsStore] — Exceptions werden NICHT geschluckt,
 * das ViewModel bzw. der Worker entscheidet über Fehleranzeige bzw. Retry.
 */
@Singleton
class NewsRepository @Inject constructor(
    private val api: HikariApi,
    private val settings: SettingsStore,
) {
    suspend fun getBriefing(force: Boolean): List<NewsItem> {
        val topics = settings.newsTopics.first().joinToString(",")
        val city = settings.newsCity.first().ifBlank { null }
        val lang = settings.newsLang.first()
        return api.getNewsBriefing(
            topics = topics,
            city = city,
            lang = lang,
            force = if (force) true else null,
        ).map { it.toDomain() }
    }

    suspend fun getTopics(): List<NewsTopicDto> = api.getNewsTopics()

    private fun NewsItemDto.toDomain() = NewsItem(
        id = id,
        title = title,
        summary = summary,
        source = source,
        url = url,
        imageUrls = imageUrls,
        videoUrl = videoUrl,
        topic = topic,
        publishedAt = publishedAt,
    )
}
