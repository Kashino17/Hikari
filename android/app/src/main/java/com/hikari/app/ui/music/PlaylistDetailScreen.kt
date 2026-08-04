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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
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

    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Text(
                entry?.playlist?.name ?: "Playlist",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HikariText,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (entry != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "Mehr", tint = HikariTextMuted)
                    }
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

        if (entry == null) {
            CenteredLoader()
            return@Column
        }

        val songs = entry.songs
        val allDownloaded = songs.isNotEmpty() && entry.downloadedCount == songs.size

        // Kopfbereich: Status + Aktionen
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(HikariSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        null,
                        tint = HikariPrimary,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (songs.size == 1) "1 Song" else "${songs.size} Songs",
                        fontSize = 14.sp,
                        color = HikariText,
                    )
                    Spacer(Modifier.height(2.dp))
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
                                songs.isEmpty() -> "leer"
                                allDownloaded -> "komplett offline verfügbar"
                                else -> "${entry.downloadedCount} von ${songs.size} offline"
                            },
                            fontSize = 12.sp,
                            color = HikariTextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { songs.firstOrNull()?.let { viewModel.play(it, songs); onOpenNowPlaying() } },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abspielen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        val shuffled = songs.shuffled()
                        shuffled.firstOrNull()?.let { viewModel.play(it, shuffled); onOpenNowPlaying() }
                    },
                    enabled = songs.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zufall", color = HikariPrimary, fontSize = 13.sp)
                }
                if (!allDownloaded && songs.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.downloadPlaylist(entry) }) {
                        Icon(Icons.Outlined.CloudDownload, null, tint = HikariPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Alle laden", color = HikariPrimary, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
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
