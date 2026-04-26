package com.hikari.app.domain.download

import com.hikari.app.data.api.dto.ChannelGroupDto
import com.hikari.app.data.api.dto.ChannelVideoEntryDto
import com.hikari.app.data.api.dto.DownloadsResponse
import com.hikari.app.data.api.dto.MovieEntryDto
import com.hikari.app.data.api.dto.SeriesEpisodeDto
import com.hikari.app.data.api.dto.SeriesGroupDto
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalDownloadKind

/**
 * Baut aus lokal vorhandenen Downloads den `DownloadsResponse`-Shape, den der
 * Profile/Downloads-Tab erwartet. Wird genutzt, wenn der Server-Call
 * `GET /downloads` fehlschlägt (Offline-Modus).
 *
 * Entscheidungen:
 *   - Series ohne seriesId  → fallen als Movie raus (defensiver Drift-Fallback).
 *   - Channel ohne channelId → künstliche Gruppe pro channelTitle, sonst Movie.
 *   - Episoden-Reihenfolge   → episode ASC, downloadedAt ASC als Tiebreaker.
 *   - Gruppen-Reihenfolge    → max(downloadedAt) DESC (zuletzt aktive oben).
 *   - Movies-Reihenfolge     → downloadedAt DESC (neueste zuerst).
 *   - total_bytes            → Summe aller Entities.
 *   - limit_bytes            → vom Aufrufer reingereicht (letzter Server-Wert
 *                              oder 0L falls nie online).
 */
object OfflineDownloadsBuilder {

    fun build(entities: List<LocalDownloadEntity>, limitBytes: Long = 0L): DownloadsResponse {
        val seriesEntries = mutableListOf<LocalDownloadEntity>()
        val channelEntries = mutableListOf<LocalDownloadEntity>()
        val movieEntries = mutableListOf<LocalDownloadEntity>()

        for (e in entities) {
            when (e.kind) {
                LocalDownloadKind.SERIES.name ->
                    if (e.seriesId != null) seriesEntries += e else movieEntries += e
                LocalDownloadKind.CHANNEL.name ->
                    if (e.channelId != null || !e.channelTitle.isNullOrBlank()) channelEntries += e
                    else movieEntries += e
                else -> movieEntries += e
            }
        }

        val seriesGroups = seriesEntries
            .groupBy { it.seriesId!! }
            .map { (seriesId, items) -> buildSeriesGroup(seriesId, items) }
            .sortedByDescending { group -> group.episodes.maxOf { it.downloaded_at } }

        val channelGroups = channelEntries
            .groupBy { it.channelId ?: "title:${it.channelTitle.orEmpty()}" }
            .map { (channelId, items) -> buildChannelGroup(channelId, items) }
            .sortedByDescending { group -> group.videos.maxOf { it.downloaded_at } }

        val movies = movieEntries
            .sortedByDescending { it.downloadedAt }
            .map { it.toMovie() }

        return DownloadsResponse(
            total_bytes = entities.sumOf { it.byteSize },
            limit_bytes = limitBytes,
            series = seriesGroups,
            channels = channelGroups,
            movies = movies,
        )
    }

    private fun buildSeriesGroup(seriesId: String, items: List<LocalDownloadEntity>): SeriesGroupDto {
        // Repräsentativer Eintrag für Series-Metadaten — der zuletzt heruntergeladene
        // hat die wahrscheinlich aktuellsten Werte (Titel-Renames, neues Cover etc.).
        val repr = items.maxBy { it.downloadedAt }
        val episodes = items
            .sortedWith(
                compareBy<LocalDownloadEntity> { it.episode ?: Int.MAX_VALUE }
                    .thenBy { it.downloadedAt },
            )
            .map { it.toEpisode() }
        return SeriesGroupDto(
            id = seriesId,
            title = repr.seriesTitle ?: repr.title,
            thumbnail_url = repr.seriesThumbnailUrl ?: repr.thumbnailUrl,
            episode_count = episodes.size,
            total_bytes = items.sumOf { it.byteSize },
            episodes = episodes,
        )
    }

    private fun buildChannelGroup(channelId: String, items: List<LocalDownloadEntity>): ChannelGroupDto {
        val repr = items.maxBy { it.downloadedAt }
        val videos = items
            .sortedByDescending { it.downloadedAt }
            .map { it.toChannelVideo() }
        return ChannelGroupDto(
            id = channelId,
            title = repr.channelTitle ?: "Unbekannter Kanal",
            thumbnail_url = repr.channelThumbnailUrl,
            banner_url = null,
            video_count = videos.size,
            total_bytes = items.sumOf { it.byteSize },
            videos = videos,
        )
    }

    private fun LocalDownloadEntity.toEpisode() = SeriesEpisodeDto(
        id = videoId,
        title = title,
        season = season,
        episode = episode,
        duration_seconds = durationSeconds,
        thumbnail_url = thumbnailUrl,
        size_bytes = byteSize,
        downloaded_at = downloadedAt,
    )

    private fun LocalDownloadEntity.toChannelVideo() = ChannelVideoEntryDto(
        id = videoId,
        title = title,
        duration_seconds = durationSeconds,
        thumbnail_url = thumbnailUrl,
        size_bytes = byteSize,
        downloaded_at = downloadedAt,
    )

    private fun LocalDownloadEntity.toMovie() = MovieEntryDto(
        id = videoId,
        title = title,
        thumbnail_url = thumbnailUrl,
        duration_seconds = durationSeconds,
        size_bytes = byteSize,
        downloaded_at = downloadedAt,
    )
}
