package com.hikari.app.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.model.MusicSong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Song-Zeile für alle Musik-Listen. Zeigt Download-Zustand als Tri-State
 * (nicht geladen / lädt / offline verfügbar) analog zu [com.hikari.app.ui.profile.components.LocalDownloadIcon].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: MusicSong,
    viewModel: MusicViewModel,
    contextQueue: List<MusicSong>,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    showHistoryDelete: Boolean = false,
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    val isCurrent = currentSong?.videoId == song.videoId
    val isFavorite = song.videoId in viewModel.favoriteIds
    val isDownloaded = song.videoId in downloadedIds
    val progress = progressMap[song.videoId]
    val playable = isDownloaded || online

    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) HikariSurfaceHigh else HikariCardBg)
            .combinedClickable(
                onClick = { viewModel.play(song, contextQueue) },
                onLongClick = { menuOpen = true },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = HikariPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = when {
                    isCurrent -> HikariPrimary
                    !playable -> HikariTextFaint
                    else -> HikariText
                },
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.uploader,
                    fontSize = 12.sp,
                    color = HikariTextMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.duration > 0) {
                    Text("  ·  ${formatDuration(song.duration)}", fontSize = 12.sp, color = HikariTextFaint)
                }
                if (!playable) {
                    Text("  ·  offline nicht verfügbar", fontSize = 11.sp, color = HikariTextFaint)
                }
            }
        }

        DownloadStateButton(
            isDownloaded = isDownloaded,
            progress = progress,
            onDownload = { viewModel.downloadSong(song) },
            onDelete = { viewModel.deleteDownload(song.videoId) },
        )

        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorit",
                tint = if (isFavorite) Color(0xFFFF5252) else HikariTextMuted,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, "Mehr", tint = HikariTextMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Zu Playlist hinzufügen") },
                    onClick = {
                        menuOpen = false
                        viewModel.addToPlaylistTarget = song
                    },
                )
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Download löschen") },
                        onClick = {
                            menuOpen = false
                            viewModel.deleteDownload(song.videoId)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Herunterladen") },
                        onClick = {
                            menuOpen = false
                            viewModel.downloadSong(song)
                        },
                    )
                }
                onRemoveFromPlaylist?.let { remove ->
                    DropdownMenuItem(
                        text = { Text("Aus Playlist entfernen") },
                        onClick = {
                            menuOpen = false
                            remove()
                        },
                    )
                }
                if (showHistoryDelete) {
                    DropdownMenuItem(
                        text = { Text("Aus Verlauf entfernen") },
                        onClick = {
                            menuOpen = false
                            viewModel.removeFromHistory(song)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadStateButton(
    isDownloaded: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    when {
        isDownloaded -> IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.OfflinePin, "Heruntergeladen", tint = HikariPrimary)
        }
        progress != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (progress <= 0f) {
                CircularProgressIndicator(color = HikariPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    color = HikariPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        else -> IconButton(onClick = onDownload) {
            Icon(Icons.Outlined.CloudDownload, "Herunterladen", tint = HikariTextMuted)
        }
    }
}

@Composable
fun OfflineBanner(text: String = "Offline — du siehst deine Downloads") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = HikariText)
    }
}

@Composable
fun CenteredLoader() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HikariPrimary)
    }
}

@Composable
fun EmptyHint(icon: ImageVector, text: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
            Text(text, color = HikariTextMuted, fontSize = 14.sp)
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

internal fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000).toInt())
