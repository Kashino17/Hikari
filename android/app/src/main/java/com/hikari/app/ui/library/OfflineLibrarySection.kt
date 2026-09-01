package com.hikari.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalMangaArcEntity
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.components.FallbackArtwork
import com.hikari.app.ui.profile.formatBytes
import com.hikari.app.ui.profile.formatDuration
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.io.File

/**
 * Bibliothek ohne Netz: zeigt ausschließlich, was wirklich auf dem Gerät liegt
 * — Videos, Manga-Arcs und Musik. Bewusst kein Fehlertext und kein
 * "Erneut versuchen"-Button: sobald wieder Netz da ist, lädt das ViewModel
 * automatisch nach.
 */
@Composable
fun OfflineLibrary(
    state: LibraryUiState.Offline,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onPlaySong: (MusicSong) -> Unit,
) {
    if (state.isEmpty) {
        OfflineEmpty()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { OfflineBanner(state) }

        if (state.videos.isNotEmpty()) {
            item {
                OfflineSectionHeader("Videos", state.videos.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.videos, key = { it.videoId }) { v ->
                        OfflineVideoCard(
                            video = v,
                            onClick = {
                                onPlayVideo(v.videoId, v.title, v.channelTitle.orEmpty())
                            },
                        )
                    }
                }
            }
        }

        if (state.mangaArcs.isNotEmpty()) {
            item {
                OfflineSectionHeader("Manga", state.mangaArcs.size)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Kein Klick: der Reader-Screen braucht Slug + Chapter und
                    // hat in LibraryScreen keinen Callback. Lieber keine Aktion
                    // als eine, die ins Leere navigiert.
                    items(state.mangaArcs, key = { it.arcId }) { arc ->
                        OfflineMangaCard(arc)
                    }
                }
            }
        }

        if (state.songs.isNotEmpty()) {
            item { OfflineSectionHeader("Musik", state.songs.size) }
            items(state.songs, key = { "song-${it.videoId}" }) { song ->
                OfflineSongRow(song = song, onClick = { onPlaySong(song) })
            }
        }
    }
}

// ─── Banner & Leerzustand ────────────────────────────────────────────────────

@Composable
private fun OfflineBanner(state: LibraryUiState.Offline) {
    val summary = remember(state) {
        listOfNotNull(
            state.videos.size.takeIf { it > 0 }?.let { if (it == 1) "1 Video" else "$it Videos" },
            state.mangaArcs.size.takeIf { it > 0 }?.let { "$it Manga" },
            state.songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 Song" else "$it Songs" },
        ).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HikariSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = HikariAmber,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                "Offline — heruntergeladene Inhalte",
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun OfflineEmpty() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = HikariTextFaint,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Offline — keine heruntergeladenen Inhalte",
            color = HikariText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "Lade Videos, Manga oder Musik herunter, solange du online bist — dann sind sie hier auch ohne Netz da.",
            color = HikariTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OfflineSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            color = HikariTextFaint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ─── Kacheln ─────────────────────────────────────────────────────────────────

@Composable
private fun OfflineVideoCard(video: LocalDownloadEntity, onClick: () -> Unit) {
    Column(modifier = Modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            // Heruntergeladene Videos haben kein lokales Standbild — die
            // Thumbnail-URL kommt offline aus Coils Disk-Cache; liegt nichts
            // im Cache, trägt das Fallback-Artwork die Fläche.
            FallbackArtwork(title = video.title)
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            startY = 120f,
                        ),
                    ),
            )
            if (video.durationSeconds > 0) {
                Text(
                    formatDuration(video.durationSeconds),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = video.title.ifBlank { "Ohne Titel" },
            color = HikariText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        val sub = listOfNotNull(
            video.channelTitle?.takeIf { it.isNotBlank() }?.uppercase(),
            video.byteSize.takeIf { it > 0 }?.let { formatBytes(it) },
        ).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(
                text = sub,
                color = HikariAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OfflineMangaCard(arc: LocalMangaArcEntity) {
    Column(modifier = Modifier.width(122.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
        ) {
            // Cover liegt als Datei auf dem Gerät — Coil lädt File-Modelle
            // direkt, ohne Netz. Fehlt die Datei, trägt das Fallback-Artwork.
            FallbackArtwork(title = arc.seriesTitle)
            AsyncImage(
                model = remember(arc.seriesCoverPath) { arc.seriesCoverPath?.let(::File) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 200f,
                        ),
                    ),
            )
            Text(
                text = arc.seriesTitle,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, end = 7.dp, bottom = 6.dp),
            )
        }
        Text(
            text = arc.arcTitle,
            color = HikariText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${arc.expectedPageCount} Seiten",
            color = HikariTextFaint,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun OfflineSongRow(song: MusicSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HikariCardBg)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HikariSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(18.dp),
            )
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = HikariText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.uploader.isNotBlank()) {
                Text(
                    text = song.uploader,
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (song.duration > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDuration(song.duration),
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
