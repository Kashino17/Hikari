package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.repo.MusicSearchMode
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Alle Songs eines kuratierten Mixes — abspielbar und offline speicherbar. */
@Composable
fun MixDetailScreen(
    title: String,
    query: String,
    mode: MusicSearchMode,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val songs = viewModel.mixSongs
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()
    val downloadedCount = songs.count { it.videoId in downloadedIds }
    val allDownloaded = songs.isNotEmpty() && downloadedCount == songs.size

    LaunchedEffect(query, mode) { viewModel.loadMix(query, mode) }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // Eine durchscrollende Liste: Hero und Aktionen scrollen mit weg,
        // nichts klebt oben fest — nur der Zurück-Chip schwebt als Overlay.
        Box(Modifier.weight(1f)) {
            when {
                viewModel.mixLoading && songs.isEmpty() -> CenteredLoader()
                songs.isEmpty() -> EmptyHint(
                    Icons.Outlined.CloudDownload,
                    "Dieser Mix ist gerade nicht erreichbar — später nochmal versuchen.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item {
                        CollectionHero(
                            imageUrl = songs.firstOrNull()?.thumbnailUrl,
                            title = title,
                            subtitle = if (songs.size == 1) "1 Song" else "${songs.size} Songs",
                        )
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (allDownloaded) Icons.Outlined.OfflinePin else Icons.Outlined.CloudDownload,
                                    null,
                                    tint = if (allDownloaded) HikariPrimary else HikariTextFaint,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    when {
                                        allDownloaded -> "komplett offline verfügbar"
                                        downloadedCount > 0 -> "$downloadedCount von ${songs.size} offline"
                                        else -> "noch nichts offline"
                                    },
                                    fontSize = 12.sp,
                                    color = HikariTextMuted,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            val saved = viewModel.playlists.any { it.playlist.name.equals(title, ignoreCase = true) }
                            val downloading = songs.any { progressMap.containsKey(it.videoId) }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CollectionSaveMenu(
                                    saved = saved,
                                    allOffline = allDownloaded,
                                    downloading = downloading,
                                    downloadedCount = downloadedCount,
                                    totalCount = songs.size,
                                    onSave = { viewModel.saveRemotePlaylist(title, songs) },
                                    onSaveAndDownload = { viewModel.saveRemotePlaylist(title, songs, thenDownload = true) },
                                    onCancelDownloads = { viewModel.cancelAllDownloads() },
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GhostIconChip(Icons.Default.Shuffle, "Zufällig") {
                                        val shuffled = songs.shuffled()
                                        shuffled.firstOrNull()?.let { viewModel.play(it, shuffled); onOpenNowPlaying() }
                                    }
                                    PlayRoundButton {
                                        songs.firstOrNull()?.let { viewModel.play(it, songs); onOpenNowPlaying() }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    itemsIndexed(songs, key = { _, s -> s.videoId }) { i, song ->
                        SongRow(
                            song,
                            viewModel,
                            songs,
                            isCurrent = currentSong?.videoId == song.videoId,
                            isDownloaded = song.videoId in downloadedIds,
                            progress = progressMap[song.videoId],
                            online = online,
                            number = i + 1,
                        )
                    }
                }
            }

            MuIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Zurück",
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.40f), CircleShape),
                tint = HikariText,
            ) { onBack() }
        }

        if (currentSong != null) {
            MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
        }
    }
}
