package com.hikari.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein heruntergeladener Manga-Arc — die Granularität an der der User
 * "Download" antippt. Pages werden separat in local_manga_pages getrackt;
 * diese Tabelle hält die UI-Metadata, damit der Profile/Downloads-Tab
 * ohne Server-Roundtrip rendern kann.
 *
 * arcId-Format: "<source>:<seriesSlug>:arc-<n>" (z.B. "onepiece-tube:one-piece:arc-7").
 */
@Entity(tableName = "local_manga_arcs")
data class LocalMangaArcEntity(
    @PrimaryKey @ColumnInfo(name = "arc_id") val arcId: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
    @ColumnInfo(name = "series_slug") val seriesSlug: String,
    @ColumnInfo(name = "series_title") val seriesTitle: String,
    @ColumnInfo(name = "series_cover_path") val seriesCoverPath: String? = null,
    @ColumnInfo(name = "arc_order") val arcOrder: Int,
    @ColumnInfo(name = "arc_title") val arcTitle: String,
    @ColumnInfo(name = "expected_page_count") val expectedPageCount: Int,
    @ColumnInfo(name = "total_byte_size") val totalByteSize: Long = 0,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
)
