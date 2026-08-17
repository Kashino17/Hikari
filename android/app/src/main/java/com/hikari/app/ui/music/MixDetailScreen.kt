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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
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
        // Hero mit Cover des ersten Songs + Zurück-Chip darüber
        Box {
            MuHeroHeader(
                imageUrl = songs.firstOrNull()?.thumbnailUrl,
                title = title,
                subtitle = when {
                    songs.isEmpty() -> null
                    songs.size == 1 -> "1 Song"
                    else -> "${songs.size} Songs"
                },
                fallbackIcon = Icons.Default.MusicNote,
                height = 220.dp,
            )
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

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
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
                        songs.isEmpty() -> "wird geladen"
                        allDownloaded -> "komplett offline verfügbar"
                        downloadedCount > 0 -> "$downloadedCount von ${songs.size} offline"
                        else -> "noch nichts offline"
                    },
                    fontSize = 12.sp,
                    color = HikariTextMuted,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MuPrimaryButton("Abspielen", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                    songs.firstOrNull()?.let { viewModel.play(it, songs); onOpenNowPlaying() }
                }
                MuGhostButton("Zufällig", Icons.Default.Shuffle, Modifier.weight(1f)) {
                    val shuffled = songs.shuffled()
                    shuffled.firstOrNull()?.let { viewModel.play(it, shuffled); onOpenNowPlaying() }
                }
            }

            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val saved = viewModel.playlists.any { it.playlist.name.equals(title, ignoreCase = true) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuActionPill(
                        icon = if (saved) Icons.Default.Check else Icons.AutoMirrored.Outlined.PlaylistAdd,
                        label = if (saved) "Gespeichert" else "Speichern",
                        active = saved,
                    ) { if (!saved) viewModel.saveRemotePlaylist(title, songs) }
                    if (!allDownloaded) {
                        MuActionPill(
                            Icons.Outlined.CloudDownload,
                            "Alle offline speichern",
                            active = false,
                        ) { viewModel.downloadMix(title) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

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
                    items(songs, key = { it.videoId }) { song ->
                        SongRow(
                            song,
                            viewModel,
                            songs,
                            isCurrent = currentSong?.videoId == song.videoId,
                            isDownloaded = song.videoId in downloadedIds,
                            progress = progressMap[song.videoId],
                            online = online,
                        )
                    }
                }
            }
        }

        if (currentSong != null) {
            MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
        }
    }
}
