package com.hikari.app.ui.music

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

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

    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Text(
                name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp)) {
                    MixCoverPreview(tracks)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (isAlbum) "Album" else "Playlist",
                        fontSize = 12.sp,
                        color = HikariTextMuted,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (tracks.size == 1) "1 Song" else "${tracks.size} Songs",
                        fontSize = 14.sp,
                        color = HikariText,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { tracks.firstOrNull()?.let { viewModel.play(it, tracks); onOpenNowPlaying() } },
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Alle abspielen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(10.dp))
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
