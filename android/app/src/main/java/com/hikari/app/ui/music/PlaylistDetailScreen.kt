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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val entry = viewModel.playlists.firstOrNull { it.playlist.id == playlistId }
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) { viewModel.refreshLibrary() }

    // Playlist gelöscht → zurück statt leerem Screen (erst nach dem ersten Laden)
    LaunchedEffect(entry, viewModel.libraryLoaded) {
        if (entry == null && viewModel.libraryLoaded) onBack()
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        if (entry == null) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText) { onBack() }
                Spacer(Modifier.width(6.dp))
                Text("Playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HikariText)
            }
            CenteredLoader()
            return@Column
        }

        val songs = entry.songs
        val allDownloaded = songs.isNotEmpty() && entry.downloadedCount == songs.size

        // Hero mit erstem Cover, Zurück und Menü als Chips darüber
        Box {
            MuHeroHeader(
                imageUrl = songs.firstOrNull()?.thumbnailUrl,
                title = entry.playlist.name,
                subtitle = when {
                    songs.isEmpty() -> "leer"
                    songs.size == 1 -> "1 Song"
                    else -> "${songs.size} Songs"
                },
                fallbackIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                height = 220.dp,
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MuIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Zurück",
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.40f), CircleShape),
                    tint = HikariText,
                ) { onBack() }
                Box {
                    MuIconButton(
                        Icons.Default.MoreVert,
                        "Mehr",
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.40f), CircleShape),
                        tint = HikariText,
                    ) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Umbenennen") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuOpen = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Playlist löschen") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.deletePlaylist(entry.playlist)
                                onBack()
                            },
                        )
                    }
                }
            }
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
                        songs.isEmpty() -> "noch keine Songs"
                        allDownloaded -> "komplett offline verfügbar"
                        else -> "${entry.downloadedCount} von ${songs.size} offline"
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

            if (!allDownloaded && songs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                MuActionPill(
                    Icons.Outlined.CloudDownload,
                    "Alle offline speichern",
                    active = false,
                ) { viewModel.downloadPlaylist(entry) }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (songs.isEmpty()) {
            EmptyHint(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                "Diese Playlist ist leer — füge Songs über das Menü eines Songs hinzu.",
            )
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(songs, key = { it.videoId }) { song ->
                        SongRow(
                            song = song,
                            viewModel = viewModel,
                            contextQueue = songs,
                            isCurrent = currentSong?.videoId == song.videoId,
                            isDownloaded = song.videoId in downloadedIds,
                            progress = progressMap[song.videoId],
                            online = online,
                            onRemoveFromPlaylist = { viewModel.removeFromPlaylist(playlistId, song) },
                        )
                    }
                }
            }
        }

        if (currentSong != null) {
            MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
        }
    }

    if (showRename && entry != null) {
        NamePlaylistDialog(
            title = "Playlist umbenennen",
            initial = entry.playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                viewModel.renamePlaylist(entry.playlist, newName)
                showRename = false
            },
        )
    }
}
