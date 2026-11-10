package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDownloadDao {
    @Query("SELECT * FROM local_downloads WHERE video_id = :id LIMIT 1")
    suspend fun get(id: String): LocalDownloadEntity?

    @Query("SELECT * FROM local_downloads ORDER BY downloaded_at DESC")
    fun observeAll(): Flow<List<LocalDownloadEntity>>

    @Query("SELECT * FROM local_downloads")
    suspend fun all(): List<LocalDownloadEntity>

    @Query("SELECT video_id FROM local_downloads")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalDownloadEntity)

    @Query("DELETE FROM local_downloads WHERE video_id = :id")
    suspend fun delete(id: String)

    /** Filmplakat und Vorschaubild eines Videos nachtragen — ein fehlender Wert lässt den alten stehen. */
    @Query(
        "UPDATE local_downloads SET poster_url = COALESCE(:posterUrl, poster_url), " +
            "thumbnail_url = COALESCE(:thumbnailUrl, thumbnail_url) WHERE video_id = :id",
    )
    suspend fun updateVideoArtwork(id: String, posterUrl: String?, thumbnailUrl: String?)

    /** Titel und Bilder einer Serie auf all ihre Folgen übertragen — ein fehlender Wert lässt den alten stehen. */
    @Query(
        "UPDATE local_downloads SET series_title = COALESCE(:title, series_title), " +
            "series_thumbnail_url = COALESCE(:thumbnailUrl, series_thumbnail_url), " +
            "series_poster_url = COALESCE(:posterUrl, series_poster_url), " +
            "series_backdrop_url = COALESCE(:backdropUrl, series_backdrop_url) WHERE series_id = :seriesId",
    )
    suspend fun updateSeriesArtwork(
        seriesId: String,
        title: String?,
        thumbnailUrl: String?,
        posterUrl: String?,
        backdropUrl: String?,
    )
}
