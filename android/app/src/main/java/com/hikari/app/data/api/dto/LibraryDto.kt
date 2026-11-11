package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val added_at: Long,
    // Seit v0.89.77 vom Server mitgeliefert; ältere Server lassen sie weg.
    val episode_count: Int = 0,
    val season_count: Int = 0,
    val last_watched_at: Long? = null,
    /** Echtes Plakat (hochkant) und Hintergrundbild (quer), falls der Server eines gefunden hat. */
    val poster_url: String? = null,
    val backdrop_url: String? = null,
    /**
     * Kategorien, die der Server nachgeschlagen hat: kommagetrennte Kennungen (`comedy,drama`).
     * Leer = nachgeschlagen, nichts gefunden. `null` = noch nicht geprüft (oder älterer Server).
     */
    val genres: String? = null,
)

@Serializable
data class LibraryVideoDto(
    val id: String,
    val channel_id: String,
    val series_id: String? = null,
    val title: String,
    val description: String? = null,
    val published_at: Long,
    val duration_seconds: Int,
    val aspect_ratio: String? = null,
    val thumbnail_url: String? = null,
    val discovered_at: Long,
    val season: Int? = null,
    val episode: Int? = null,
    val channelTitle: String? = null,
    val progress_seconds: Float? = null,
    /** Zeitpunkt des letzten Fortschritts-Updates — für „Weiterschauen“. */
    val progress_updated_at: Long? = null,
    val overall_score: Int? = null,
    /**
     * 1, wenn die Datei tatsächlich auf dem Server liegt. Eine videos-Zeile
     * allein heißt das nicht: Ein abgebrochener Import oder eine weggeräumte
     * Datei hinterlässt eine Folge, die sich nicht abspielen lässt. Default 1,
     * damit ältere Server (die das Feld nicht senden) unverändert wirken.
     */
    val downloaded: Int = 1,
    val is_movie: Int = 0,
    /** Echtes Filmplakat, falls der Server eines gefunden hat. */
    val poster_url: String? = null,
    /** Kategorien vom Server, wie bei [SeriesDto.genres] — nur Filme bekommen welche. */
    val genres: String? = null,
    /** Weiterschauen: 1 = die vorige Folge ist zu Ende gesehen, das hier ist die nächste. */
    val up_next: Int = 0,
)

/**
 * channel_id "manual" = selbst importiert (Browser/Teilen), sonst YouTube-Kanal.
 * Film = ausdrücklich als Film markiert, oder ein eigener Import ohne Serie.
 * YouTube-Videos sind nie Filme — egal wie lang oder wie der Titel lautet.
 */
fun LibraryVideoDto.isFilm(): Boolean =
    series_id == null && (is_movie == 1 || channel_id == "manual")

/** Gehört zum Archiv (Serienfolge oder Film) — im Gegensatz zu einem YouTube-Video. */
fun LibraryVideoDto.isArchive(): Boolean = series_id != null || isFilm()


@Serializable
data class LibraryResponse(
    val series: List<SeriesDto>,
    val recentlyAdded: List<LibraryVideoDto>,
    val channels: List<ChannelDto>,
    // Etappe 5: die Sammlung — optional, damit alte Server weiter parsen.
    val watchLater: List<FeedItemDto> = emptyList(),
    val history: List<FeedItemDto> = emptyList(),
    // Empfehlungen aus abonnierten Kanälen — optional, damit ältere Server
    // (ohne das Feld) weiter parsen.
    val subscriptionPicks: List<FeedItemDto> = emptyList(),
    // Seit v0.89.70: Verlauf/Weiterschauen über alle Quellen (auch gestreamte
    // YouTube-Videos) und die neuesten Uploads der abonnierten Kanäle.
    val watchHistory: List<LibraryVideoDto> = emptyList(),
    val continueWatching: List<LibraryVideoDto> = emptyList(),
    val channelUploads: List<LibraryVideoDto> = emptyList(),
)

@Serializable
data class SeriesDetailResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val added_at: Long,
    val videos: List<LibraryVideoDto>,
    val poster_url: String? = null,
    val backdrop_url: String? = null,
)

@Serializable
data class UpdateSeriesRequest(
    val thumbnail_url: String? = null,
    val description: String? = null,
)

@Serializable
data class MergeSeriesRequest(
    val sourceId: String,
    val targetId: String,
)

/** Antwort von GET /videos/{id}/next — die nächste Folge derselben Serie. */
@Serializable
data class NextVideoDto(
    val id: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class VideoDetailDto(
    val id: String,
    val channel_id: String,
    val series_id: String? = null,
    val series_title: String? = null,
    val title: String,
    val description: String? = null,
    val published_at: Long = 0L,
    val duration_seconds: Int = 0,
    val thumbnail_url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val dub_language: String? = null,
    val sub_language: String? = null,
    val is_movie: Int = 0,
    /** Ursprungsseite importierter Folgen (Browser-Import) — Ziel beim Teilen. */
    val source_url: String? = null,
)

@Serializable
data class UpdateVideoRequest(
    val title: String? = null,
    val description: String? = null,
    val thumbnail_url: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val dub_language: String? = null,
    val sub_language: String? = null,
    val is_movie: Boolean? = null,
    val series_id: String? = null,
    val series_title: String? = null,
)

@Serializable
data class CastInfoDto(val lanBaseUrls: List<String> = emptyList())
