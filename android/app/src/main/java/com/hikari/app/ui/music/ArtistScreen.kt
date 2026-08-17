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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.domain.model.ArtistAlbum
import com.hikari.app.domain.model.ArtistPlaylist
import com.hikari.app.domain.model.SearchArtist
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val PLAYLIST_CARD_WIDTH = 208.dp
private val PLAYLIST_CARD_HEIGHT = 117.dp

/**
 * Artist-Seite im YouTube-Music-Stil: Profil, Top-Songs, Alben, Singles,
 * Playlists und ähnliche Künstler — alles aus einem Backend-Call, garantiert
 * vom richtigen Kanal. [fallbackName] dient nur noch dem Fallback gegen alte
 * Backends ohne den Page-Endpunkt.
 */
@Composable
fun ArtistScreen(
    channelId: String,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenCollection: (playlistId: String, name: String, isAlbum: Boolean) -> Unit,
    onOpenArtist: (channelId: String, name: String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val page = viewModel.artistPage
    val artist = page?.artist
    val topSongs = page?.topSongs.orEmpty()
    val playlists = page?.playlists.orEmpty()
    val currentSong by viewModel.player.currentSong.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val progressMap by viewModel.downloadProgress.collectAsState()
    val online by viewModel.isOnline.collectAsState()

    LaunchedEffect(channelId, fallbackName) { viewModel.loadArtist(channelId, fallbackName) }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    viewModel.artistLoading && artist == null -> CenteredLoader()
                    viewModel.artistFailed && artist == null -> Column(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = HikariTextFaint, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Künstler konnte nicht geladen werden",
                            color = HikariTextMuted,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        MuGhostButton("Erneut versuchen", Icons.Default.Refresh) {
                            viewModel.loadArtist(channelId, fallbackName)
                        }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        // Hero: Künstlerfoto als großes Banner, Name + Abo-Zahl im Scrim
                        item(key = "header") {
                            MuHeroHeader(
                                imageUrl = artist?.avatarUrl,
                                title = artist?.name ?: fallbackName,
                                subtitle = artist?.subscriberCount
                                    ?.takeIf { it > 0 }
                                    ?.let { formatSubscribersDE(it) },
                                fallbackIcon = Icons.Default.MusicNote,
                                height = 260.dp,
                            )
                        }

                        val verified = artist?.verified == true
                        val description = artist?.description.orEmpty()
                        if (verified || description.isNotBlank()) {
                            item(key = "about") {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    if (verified) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Verified,
                                                null,
                                                tint = HikariPrimary,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "Verifizierter Künstler",
                                                fontSize = 12.sp,
                                                color = HikariPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (description.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            description,
                                            fontSize = 13.sp,
                                            color = HikariTextMuted,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // Abspielen/Zufällig starten die Top-Songs als Queue.
                        if (topSongs.isNotEmpty()) {
                            item(key = "actions") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    MuPrimaryButton(
                                        "Abspielen",
                                        Icons.Default.PlayArrow,
                                        Modifier.weight(1f),
                                    ) { viewModel.play(topSongs.first(), topSongs) }
                                    MuGhostButton(
                                        "Zufällig",
                                        Icons.Default.Shuffle,
                                        Modifier.weight(1f),
                                    ) {
                                        val shuffled = topSongs.shuffled()
                                        viewModel.play(shuffled.first(), shuffled)
                                    }
                                }
                            }
                            item(key = "top-header") { SectionHeader("Top-Songs") }
                            items(topSongs, key = { "top-${it.videoId}" }) { song ->
                                SongRow(
                                    song,
                                    viewModel,
                                    topSongs,
                                    isCurrent = currentSong?.videoId == song.videoId,
                                    isDownloaded = song.videoId in downloadedIds,
                                    progress = progressMap[song.videoId],
                                    online = online,
                                    badge = if (song.views > 0) formatViewsDE(song.views) else null,
                                )
                            }
                        }

                        val albums = page?.albums.orEmpty()
                        if (albums.isNotEmpty()) {
                            item(key = "albums-header") { SectionHeader("Alben") }
                            item(key = "albums-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(albums, key = { "al-${it.playlistId}" }) { album ->
                                        ArtistAlbumCard(
                                            album = album,
                                            onClick = { onOpenCollection(album.playlistId, album.name, true) },
                                        )
                                    }
                                }
                            }
                        }

                        val singles = page?.singles.orEmpty()
                        if (singles.isNotEmpty()) {
                            item(key = "singles-header") { SectionHeader("Singles & EPs") }
                            item(key = "singles-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(singles, key = { "si-${it.playlistId}" }) { single ->
                                        ArtistAlbumCard(
                                            album = single,
                                            onClick = { onOpenCollection(single.playlistId, single.name, true) },
                                        )
                                    }
                                }
                            }
                        }

                        if (playlists.isNotEmpty()) {
                            item(key = "playlists-header") { SectionHeader("Playlists") }
                            item(key = "playlists-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(playlists, key = { "pl-${it.playlistId}" }) { playlist ->
                                        ArtistPlaylistCard(
                                            playlist = playlist,
                                            onClick = { onOpenCollection(playlist.playlistId, playlist.name, false) },
                                        )
                                    }
                                }
                            }
                        }

                        val related = page?.related.orEmpty()
                        if (related.isNotEmpty()) {
                            item(key = "related-header") { SectionHeader("Ähnliche Künstler") }
                            item(key = "related-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(related, key = { "re-${it.channelId}" }) { rel ->
                                        RelatedArtistBubble(
                                            artist = rel,
                                            onClick = { onOpenArtist(rel.channelId, rel.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (currentSong != null) {
                MiniPlayerBar(controller = viewModel.player, onOpen = onOpenNowPlaying)
            }
        }

        // Zurück-Chip schwebt über Hero bzw. Lade-/Fehlerzustand
        MuIconButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Zurück",
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.40f), CircleShape),
            tint = HikariText,
        ) { onBack() }
    }
}

/** Album-/Single-Karte: quadratisches Cover, Name und Jahr darunter. */
@Composable
private fun ArtistAlbumCard(album: ArtistAlbum, onClick: () -> Unit) {
    Column(Modifier.width(140.dp)) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .muPressable(onClick = onClick),
        ) {
            if (album.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = album.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(36.dp).align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = HikariText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        album.year?.let {
            Text("$it", fontSize = 11.sp, color = HikariTextMuted)
        }
    }
}

/** Runder Avatar eines ähnlichen Künstlers — Tipp öffnet dessen Seite. */
@Composable
private fun RelatedArtistBubble(artist: SearchArtist, onClick: () -> Unit) {
    Column(
        Modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh)
                .muPressable(onClick = onClick),
        ) {
            if (artist.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(30.dp).align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            artist.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = HikariText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Playlist-Karte: Cover mit Scrim, Name und Songanzahl direkt auf dem Bild. */
@Composable
private fun ArtistPlaylistCard(playlist: ArtistPlaylist, onClick: () -> Unit) {
    Box(
        Modifier
            .width(PLAYLIST_CARD_WIDTH)
            .height(PLAYLIST_CARD_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariSurfaceHigh)
            .muPressable(onClick = onClick),
    ) {
        if (playlist.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                null,
                tint = HikariTextFaint,
                modifier = Modifier.size(36.dp).align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                playlist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HikariText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (playlist.videoCount == 1) "1 Song" else "${playlist.videoCount} Songs",
                fontSize = 11.sp,
                color = HikariText.copy(alpha = 0.75f),
            )
        }
    }
}
