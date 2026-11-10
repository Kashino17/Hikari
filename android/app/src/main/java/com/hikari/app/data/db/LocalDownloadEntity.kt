package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Was an Inhalt hier liegt = was offline abspielbar ist. Reichhaltige Metadaten
 * werden zum Download-Zeitpunkt mitgeschrieben, damit der Profile/Downloads-Tab
 * vollständig ohne Server-Roundtrip rendern kann.
 */
@Entity(tableName = "local_downloads")
data class LocalDownloadEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "local_file_path") val localFilePath: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int = 0,

    /** SERIES | CHANNEL | MOVIE — gespeichert als String für Forward-Kompat. */
    @ColumnInfo(name = "kind") val kind: String = LocalDownloadKind.MOVIE.name,

    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String? = null,

    // Channel-Bezug (gesetzt für CHANNEL und Smart-Downloads aus dem Feed)
    @ColumnInfo(name = "channel_id") val channelId: String? = null,
    @ColumnInfo(name = "channel_title") val channelTitle: String? = null,
    @ColumnInfo(name = "channel_thumbnail_url") val channelThumbnailUrl: String? = null,

    // Series-Bezug (gesetzt nur bei SERIES)
    @ColumnInfo(name = "series_id") val seriesId: String? = null,
    @ColumnInfo(name = "series_title") val seriesTitle: String? = null,
    @ColumnInfo(name = "series_thumbnail_url") val seriesThumbnailUrl: String? = null,
    @ColumnInfo(name = "season") val season: Int? = null,
    @ColumnInfo(name = "episode") val episode: Int? = null,

    /**
     * Woher der Download kommt: MANUAL (selbst angestoßen) oder SMART (von den
     * Smart-Downloads geholt). Danach richtet sich, wie lange er bleibt.
     */
    @ColumnInfo(name = "source", defaultValue = "MANUAL") val source: String = LocalDownloadSource.MANUAL.name,

    /**
     * Echte Plakate, wie die Bibliothek sie zeigt: das Filmplakat des Videos
     * selbst sowie Plakat (hochkant) und Hintergrund (quer) seiner Serie. Die
     * Bilddateien dazu liegen auf dem Gerät (OfflineArtworkStore) — so sieht
     * die Offline-Bibliothek aus wie die echte.
     */
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "series_poster_url") val seriesPosterUrl: String? = null,
    @ColumnInfo(name = "series_backdrop_url") val seriesBackdropUrl: String? = null,
)

enum class LocalDownloadKind { SERIES, CHANNEL, MOVIE }

enum class LocalDownloadSource { MANUAL, SMART }
