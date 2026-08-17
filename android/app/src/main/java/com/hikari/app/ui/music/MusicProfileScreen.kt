package com.hikari.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hikari.app.domain.repo.PlaylistWithSongs
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import java.io.File

private const val LIB_PLAYLISTS = 0
private const val LIB_DOWNLOADS = 1
private const val LIB_FAVORITES = 2

/**
 * Die eigene Musik-Bibliothek hinter dem Profil-Icon: großer Avatar mit
 * Kennzahlen, darunter Playlists, Downloads und Favoriten als Segmente.
 * Die Scroll-Zustände der Listen leben im ViewModel und überleben die
 * Navigation.
 */
@Composable
fun MusicProfileScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    var segment by rememberSaveable { mutableStateOf(LIB_PLAYLISTS) }
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val avatarPath by viewModel.avatarPath.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    viewModel.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbar.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Kopf: Zurück-Chip, Titel, Avatar mit Kennzahlen
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText) { onBack() }
                Spacer(Modifier.width(4.dp))
                Text("Deine Bibliothek", fontSize = 19.sp, fontWeight = FontWeight.Black, color = HikariText)
            }

            MuAppear(0) {
                LibraryHeader(
                    avatarPath = avatarPath,
                    playlists = viewModel.playlists.size,
                    downloads = downloadedSongs.size,
                    favorites = viewModel.favorites.size,
                )
            }

            MuAppear(1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MuChip("Playlists", active = segment == LIB_PLAYLISTS) { segment = LIB_PLAYLISTS }
                    MuChip("Downloads", active = segment == LIB_DOWNLOADS) { segment = LIB_DOWNLOADS }
                    MuChip("Favoriten", active = segment == LIB_FAVORITES) { segment = LIB_FAVORITES }
                }
            }

            Box(Modifier.weight(1f)) {
                when (segment) {
                    LIB_PLAYLISTS -> PlaylistsSection(viewModel, onOpenPlaylist)
                    LIB_DOWNLOADS -> DownloadsSection(viewModel)
                    else -> FavoritesSection(viewModel)
                }
            }

            if (currentSong != null) {
                MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = HikariSurfaceHigh,
                contentColor = HikariText,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

/** Großer Avatar (gleiches Bild wie im App-Profil) + drei Kennzahlen. */
@Composable
private fun LibraryHeader(avatarPath: String?, playlists: Int, downloads: Int, favorites: Int) {
    val avatarFile = remember(avatarPath) {
        avatarPath?.substringBefore("?")?.let(::File)?.takeIf { it.exists() }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(HikariCardBg)
                .border(
                    2.dp,
                    Brush.sweepGradient(
                        listOf(HikariPrimary, HikariPrimary.copy(alpha = 0.25f), HikariPrimary),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarFile != null) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = "Profilbild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.Person, null, tint = HikariTextMuted, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.width(18.dp))
        LibraryStat(playlists, "Playlists", Modifier.weight(1f))
        LibraryStat(downloads, "Offline", Modifier.weight(1f))
        LibraryStat(favorites, "Favoriten", Modifier.weight(1f))
    }
}

@Composable
private fun LibraryStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Black, color = HikariPrimary)
        Text(label, fontSize = 11.sp, color = HikariTextMuted)
    }
}

@Composable
private fun PlaylistsSection(viewModel: MusicViewModel, onOpenPlaylist: (Int) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (viewModel.playlists.size == 1) "1 Playlist" else "${viewModel.playlists.size} Playlists",
                fontSize = 13.sp,
                color = HikariTextMuted,
                modifier = Modifier.weight(1f),
            )
            MuChip("Neue Playlist", active = true, icon = Icons.Default.Add, onClick = { showCreate = true })
        }

        if (viewModel.playlists.isEmpty()) {
            EmptyHint(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                "Noch keine Playlists — tippe auf „Neu“ oder lange auf einen Song.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = viewModel.playlistsListState,
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(viewModel.playlists, key = { it.playlist.id }) { entry ->
                    LibraryPlaylistCard(entry, onClick = { onOpenPlaylist(entry.playlist.id) })
                }
            }
        }
    }

    if (showCreate) {
        NamePlaylistDialog(
            title = "Neue Playlist",
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun LibraryPlaylistCard(entry: PlaylistWithSongs, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .muPressable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover des ersten Songs als Playlist-Thumbnail; leer → dezentes Icon.
        val cover = entry.songs.firstOrNull { it.thumbnailUrl.isNotEmpty() }?.thumbnailUrl
        if (cover != null) {
            val thumbPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover)
                    .size(thumbPx)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurfaceHigh),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.playlist.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = HikariText, maxLines = 1)
            val songLabel = if (entry.songs.size == 1) "1 Song" else "${entry.songs.size} Songs"
            val offlineLabel = when {
                entry.songs.isEmpty() -> ""
                entry.downloadedCount == entry.songs.size -> "  ·  komplett offline"
                entry.downloadedCount > 0 -> "  ·  ${entry.downloadedCount} offline"
                else -> ""
            }
            Text("$songLabel$offlineLabel", fontSize = 12.sp, color = HikariTextMuted)
        }
    }
}

@Composable
private fun DownloadsSection(viewModel: MusicViewModel) {
    val songs by viewModel.downloadedSongs.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    if (songs.isEmpty()) {
        EmptyHint(
            Icons.Outlined.CloudDownload,
            "Noch nichts heruntergeladen — tippe bei einem Song auf das Download-Symbol.",
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (songs.size == 1) "1 Song offline verfügbar" else "${songs.size} Songs offline verfügbar",
            fontSize = 13.sp,
            color = HikariTextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = viewModel.downloadsListState,
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

@Composable
private fun FavoritesSection(viewModel: MusicViewModel) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    if (viewModel.favorites.isEmpty()) {
        EmptyHint(Icons.Default.FavoriteBorder, "Tippe das Herz bei einem Song — er landet hier.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = viewModel.favoritesListState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(viewModel.favorites, key = { it.videoId }) { song ->
            SongRow(
                song,
                viewModel,
                viewModel.favorites,
                isCurrent = currentSong?.videoId == song.videoId,
                isDownloaded = song.videoId in downloadedIds,
                progress = progressMap[song.videoId],
                online = online,
            )
        }
    }
}
