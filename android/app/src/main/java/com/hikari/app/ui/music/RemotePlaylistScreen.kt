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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariText

/**
 * Detail-Seite einer Remote-Playlist oder eines Albums aus der Suche —
 * dieselbe Anmutung wie die Mix-Seite, nur dass die Tracks über die
 * Playlist-Id geladen werden statt über eine Suche.
 */
@Composable
fun RemotePlaylistScreen(
    playlistId: String,
    name: String,
    isAlbum: Boolean,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val tracks = viewModel.remotePlaylistTracks
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    LaunchedEffect(playlistId) { viewModel.loadRemotePlaylist(playlistId) }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Box {
            MuHeroHeader(
                imageUrl = tracks.firstOrNull()?.thumbnailUrl,
                title = name,
                subtitle = buildString {
                    append(if (isAlbum) "Album" else "Playlist")
                    if (tracks.isNotEmpty()) {
                        append(" · ")
                        append(if (tracks.size == 1) "1 Song" else "${tracks.size} Songs")
                    }
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
            Spacer(Modifier.height(12.dp))
            MuPrimaryButton("Alle abspielen", Icons.Default.PlayArrow, Modifier.fillMaxWidth()) {
                tracks.firstOrNull()?.let { viewModel.play(it, tracks); onOpenNowPlaying() }
            }
            Spacer(Modifier.height(8.dp))
            // Speichern legt eine lokale Playlist an; Offline lädt zusätzlich
            // alle Songs herunter — der Kurzweg zum Offline-Hören.
            val saved = viewModel.playlists.any { it.playlist.name.equals(name, ignoreCase = true) }
            val downloadedCount = tracks.count { it.videoId in downloadedIds }
            val allOffline = tracks.isNotEmpty() && downloadedCount == tracks.size
            val downloading = tracks.any { progressMap.containsKey(it.videoId) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuActionPill(
                    icon = if (saved) Icons.Default.Check else Icons.AutoMirrored.Outlined.PlaylistAdd,
                    label = if (saved) "Gespeichert" else "Speichern",
                    active = saved,
                ) {
                    if (!saved && tracks.isNotEmpty()) viewModel.saveRemotePlaylist(name, tracks)
                }
                MuActionPill(
                    icon = if (allOffline) Icons.Default.DownloadDone else Icons.Outlined.CloudDownload,
                    label = when {
                        allOffline -> "Offline"
                        downloading -> "✕ Abbrechen $downloadedCount/${tracks.size}"
                        else -> "Offline speichern"
                    },
                    active = allOffline,
                    activeColor = Color(0xFF4ADE80),
                ) {
                    when {
                        // Läuft: Tipp bricht alle ausstehenden Downloads ab
                        downloading -> viewModel.cancelAllDownloads()
                        !allOffline && tracks.isNotEmpty() ->
                            viewModel.saveRemotePlaylist(name, tracks, thenDownload = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                viewModel.remotePlaylistLoading && tracks.isEmpty() -> CenteredLoader()
                tracks.isEmpty() -> EmptyHint(
                    Icons.Outlined.CloudDownload,
                    if (isAlbum) {
                        "Dieses Album ist gerade nicht erreichbar — später nochmal versuchen."
                    } else {
                        "Diese Playlist ist gerade nicht erreichbar — später nochmal versuchen."
                    },
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(tracks, key = { it.videoId }) { song ->
                        SongRow(
                            song,
                            viewModel,
                            tracks,
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
